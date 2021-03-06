package ca.waterloo.dsg.graphflow.query.planner;

import ca.waterloo.dsg.graphflow.graph.Graph.Direction;
import ca.waterloo.dsg.graphflow.graph.Graph.GraphVersion;
import ca.waterloo.dsg.graphflow.graph.TypeAndPropertyKeyStore;
import ca.waterloo.dsg.graphflow.query.operator.AbstractOperator;
import ca.waterloo.dsg.graphflow.query.operator.EdgeIdResolver;
import ca.waterloo.dsg.graphflow.query.operator.Extend;
import ca.waterloo.dsg.graphflow.query.operator.FileOutputSink;
import ca.waterloo.dsg.graphflow.query.operator.Filter;
import ca.waterloo.dsg.graphflow.query.operator.Scan;
import ca.waterloo.dsg.graphflow.query.operator.UDFSink;
import ca.waterloo.dsg.graphflow.query.operator.genericjoin.EdgeIntersectionRule;
import ca.waterloo.dsg.graphflow.query.operator.genericjoin.StageOperator;
import ca.waterloo.dsg.graphflow.query.operator.udf.UDFAction;
import ca.waterloo.dsg.graphflow.query.operator.udf.UDFResolver;
import ca.waterloo.dsg.graphflow.query.plans.ContinuousMatchQueryPlan;
import ca.waterloo.dsg.graphflow.query.plans.OneTimeMatchQueryPlan;
import ca.waterloo.dsg.graphflow.query.plans.QueryPlan;
import ca.waterloo.dsg.graphflow.query.structuredquery.QueryRelation;
import ca.waterloo.dsg.graphflow.query.structuredquery.StructuredQuery;
import ca.waterloo.dsg.graphflow.query.structuredquery.StructuredQuery.QueryOperation;
import ca.waterloo.dsg.graphflow.util.UsedOnlyByTests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a {@code ContinuousMatchQueryPlan} to continuously find changes to MATCH query
 * results specified in the limited Cypher language Graphflow supports.
 */
public class ContinuousMatchQueryPlanner extends OneTimeMatchQueryPlanner {

    /**
     * @param structuredQuery query to plan.
     */
    public ContinuousMatchQueryPlanner(StructuredQuery structuredQuery) throws IOException,
        ClassNotFoundException, InstantiationException, IllegalAccessException {
        super(structuredQuery, null /* outputSink not yet set */);
        if (QueryOperation.CONTINUOUS_EXPLAIN != structuredQuery.getQueryOperation()) {
            if (null != structuredQuery.getFilePath()) {
                outputSink = new FileOutputSink(new File(structuredQuery.getFilePath()));
            } else {
                outputSink = new UDFSink(UDFResolver.getUDFObject(structuredQuery.
                    getContinuousMatchOutputLocation(), structuredQuery.
                    getContinuousMatchAction()));
            }
        }
    }

    @UsedOnlyByTests
    public ContinuousMatchQueryPlanner(StructuredQuery structuredQuery, File location)
        throws IOException {
        super(structuredQuery, new FileOutputSink(location));
    }

    @UsedOnlyByTests
    public ContinuousMatchQueryPlanner(StructuredQuery structuredQuery, UDFAction udfAction)
        throws IOException {
        super(structuredQuery, new UDFSink(udfAction));
    }

    /**
     * Creates a continuous {@code MATCH} query plan for the given {@code structuredQuery}.
     *
     * @return A {@link QueryPlan} encapsulating a {@link ContinuousMatchQueryPlan}.
     */
    @Override
    public QueryPlan plan() {
        ContinuousMatchQueryPlan continuousMatchQueryPlan = new ContinuousMatchQueryPlan(
            outputSink);
        // We construct as many delta queries as there are relations in the query graph. Let n be
        // the number of relations in the query graph. Then we have dQ1, dQ2, ..., dQn. Delta query
        // dQi consists of the following: (1) i-1 relations that use the {@code MERGED} version
        // of the graph (newly added edges + the permanent edges); (2) one relation that use only
        // the {@code DIFF_PLUS} or {@code DIFF_MINUS} versions of the graph (the newly added or
        // deleted edges). We refer to this relation as the diffRelation below; (3) n-i relations
        // that use the {@code PERMANENT} version of the graph.
        Set<QueryRelation> mergedRelations = new HashSet<>();
        OneTimeMatchQueryPlan queryPlan;
        AbstractOperator nextOperator;
        Set<QueryRelation> permanentRelations = new HashSet<>(structuredQuery.getQueryRelations());
        for (QueryRelation diffRelation : structuredQuery.getQueryRelations()) {
            // The first two variables considered in each round will be the variables from the
            // delta relation.
            permanentRelations.remove(diffRelation);
            List<String> orderedVariables = new ArrayList<>();
            orderedVariables.add(diffRelation.getFromQueryVariable().getVariableName());
            orderedVariables.add(diffRelation.getToQueryVariable().getVariableName());
            super.orderRemainingVariables(orderedVariables);

            // Create the query plan using the ordering determined above.
            nextOperator = getNextOperator(orderedVariables);
            queryPlan = addSingleQueryPlan(
                GraphVersion.DIFF_PLUS, orderedVariables, diffRelation, permanentRelations,
                mergedRelations, nextOperator);
            continuousMatchQueryPlan.addOneTimeMatchQueryPlan(queryPlan);
            queryPlan = addSingleQueryPlan(
                GraphVersion.DIFF_MINUS, orderedVariables, diffRelation, permanentRelations,
                mergedRelations, nextOperator);
            continuousMatchQueryPlan.addOneTimeMatchQueryPlan(queryPlan);
            mergedRelations.add(diffRelation);
        }
        return continuousMatchQueryPlan;
    }

    /**
     * Adds to the delta query plans the next set of operators. The op1->op2 below indicates that
     * operator op1 appends results to operator op2.
     * Delta queries always append to {@link EdgeIdResolver}->({@link Filter})?->{@link UDFSink}
     * or {@link FileOutputSink}.
     */
    AbstractOperator getNextOperator(List<String> orderedVertexVariables) {
        AbstractOperator nextOperator = outputSink;

        Map<String, Integer> orderedVariableIndexMap = getOrderedVariableIndexMap(
            orderedVertexVariables);

        List<String> orderedEdgeVariables = giveAllQueryRelationsVariableNames();

        // Construct the {@code Filter} operator if needed.
        if (!structuredQuery.getQueryPropertyPredicates().isEmpty()) {
            Map<String, Integer> orderedEdgeIndexMap = getOrderedVariableIndexMap(
                orderedEdgeVariables);
            nextOperator = constructFilter(orderedVariableIndexMap, orderedEdgeIndexMap,
                nextOperator);
        }

        return constructEdgeIdResolver(orderedEdgeVariables, orderedVariableIndexMap, nextOperator);
    }

    /**
     * Returns the query plan for a single delta query in the {@code ContinuousMatchQueryPlan}.
     *
     * @param orderedVariables The order in which variables will be covered in the plan.
     * @param diffRelation The relation which will use the diff graph for a single delta query in
     * the {@code ContinuousMatchQueryPlan}.
     * @param permanentRelations The set of relations that uses the {@link GraphVersion#PERMANENT}
     * version of the graph.
     * @param mergedRelations The set of relations that uses the {@link GraphVersion#MERGED} version
     * of the graph.
     *
     * @return OneTimeMatchQueryPlan A set of stages representing a single generic join query plan.
     */
    private OneTimeMatchQueryPlan addSingleQueryPlan(GraphVersion graphVersion,
        List<String> orderedVariables, QueryRelation diffRelation,
        Set<QueryRelation> permanentRelations, Set<QueryRelation> mergedRelations,
        AbstractOperator nextOperator) {
        OneTimeMatchQueryPlan plan = new OneTimeMatchQueryPlan();
        // Store variable ordering in {@link OneTimeMatchQueryPlan}
        plan.setOrderedVariables(orderedVariables);
        List<EdgeIntersectionRule> stage;
        // Add the first stage. The first stage always starts with extending the diffRelation's
        // {@code fromVariable} to {@code toVariable} with the type on the relation.
        stage = new ArrayList<>();
        stage.add(new EdgeIntersectionRule(0, Direction.FORWARD, graphVersion,
            TypeAndPropertyKeyStore.getInstance().mapStringTypeToShort(diffRelation.
                getRelationType())));
        Map<String, Integer> variableIndicesMap = getVariableIndicesMap(orderedVariables);

        // Add the other relations that are present between the diffRelation's
        // {@code fromVariable} to {@code toVariable}.
        String fromVariable = orderedVariables.get(0);
        String toVariable = orderedVariables.get(1);
        for (QueryRelation queryRelation : queryGraph.getAdjacentRelations(fromVariable,
            toVariable)) {
            if (QueryRelation.isSameAs(diffRelation, queryRelation)) {
                // This relation has been added as the {@code diffRelation}.
                continue;
            }
            addGenericJoinIntersectionRule(0,
                // The {@code Direction} of the rule is {@code FORWARD} if {@code queryRelation} is
                // an edge from {@code fromVariable} to {@code toVariable}, else {@code BACKWARD}.
                queryRelation.getFromQueryVariable().getVariableName().equals(fromVariable) ?
                    Direction.FORWARD : Direction.BACKWARD,
                queryRelation, stage, permanentRelations, mergedRelations);
        }
        StageOperator previousStageOperator;
        StageOperator currentStageOperator = new Scan(stage, TypeAndPropertyKeyStore.getInstance().
            mapStringTypeToShort(diffRelation.getFromQueryVariable().getVariableType()),
            TypeAndPropertyKeyStore.getInstance().mapStringTypeToShort(diffRelation.
                getToQueryVariable().getVariableType()));
        plan.setFirstOperator(currentStageOperator);

        // Add the rest of the stages.
        String toVertexTypeFilterAsString = null;
        for (int i = 2; i < orderedVariables.size(); i++) {
            String nextVariable = orderedVariables.get(i);
            // We add a new stage that consists of the following intersection rules. For each
            // relation that is between the {@code nextVariable} and one of the previously
            // {@code coveredVariable}, we add a new intersection rule. The direction of the
            // intersection rule is {@code FORWARD} if the relation is from {@code coveredVariable}
            // to {@code nextVariable), otherwise the direction is {@code BACKWARD}. This
            // is because we essentially extend prefixes from the {@code coveredVariable}s to the
            // {@code nextVariable}s. The type of the intersection rule is the type on the relation.
            stage = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                String coveredVariable = orderedVariables.get(j);
                if (queryGraph.containsRelation(coveredVariable, nextVariable)) {
                    for (QueryRelation queryRelation : queryGraph.getAdjacentRelations(
                        coveredVariable, nextVariable)) {
                        addGenericJoinIntersectionRule(j,
                            // The {@code Direction} of the rule is {@code FORWARD} if
                            // {@code queryRelation} is an edge from {@code coveredVariable} to
                            // {@code nextVariable}, else {@code BACKWARD}.
                            queryRelation.getFromQueryVariable().getVariableName().equals(
                                coveredVariable) ? Direction.FORWARD : Direction.BACKWARD,
                            queryRelation, stage, permanentRelations, mergedRelations);
                        toVertexTypeFilterAsString = queryRelation.getToQueryVariable().
                            getVariableType();
                    }
                }
            }
            previousStageOperator = currentStageOperator;
            currentStageOperator = new Extend(stage, TypeAndPropertyKeyStore.getInstance().
                mapStringTypeToShort(toVertexTypeFilterAsString));
            previousStageOperator.nextOperator = currentStageOperator;
        }

        currentStageOperator.nextOperator = nextOperator;
        currentStageOperator.setMatchQueryOutput(plan.getFirstOperator().
            getMatchQueryResultType(), variableIndicesMap);
        return plan;
    }

    /**
     * Adds a {@code EdgeIntersectionRule} to the given stage with the given
     * {@code prefixIndex}, {@code direction} and the relation and variable type IDs, if the
     * {@code newRelation} exists in either {@code permanentRelations} or {@code mergedRelations}.
     *
     * @param prefixIndex Prefix index of the {@code EdgeIntersectionRule} to be created.
     * @param direction Direction from the covered variable to the variable under consideration.
     * @param newRelation The relation for which the rule is being added.
     * @param stage The generic join stage to which the intersection rule will be added.
     * @param permanentRelations The set of relations that uses the {@link GraphVersion#PERMANENT}
     * version of the graph.
     * @param mergedRelations The set of relations that uses the {@link GraphVersion#MERGED} version
     * of the graph.
     */
    private void addGenericJoinIntersectionRule(int prefixIndex, Direction direction,
        QueryRelation newRelation, List<EdgeIntersectionRule> stage,
        Set<QueryRelation> permanentRelations, Set<QueryRelation> mergedRelations) {
        // Select the appropriate {@code GraphVersion} by checking for the existence of
        // {@code newRelation} in either {@code mergedRelations} or {@code mergedRelations}.
        GraphVersion version;
        if (isRelationPresentInSet(newRelation, mergedRelations)) {
            version = GraphVersion.MERGED;
        } else if (isRelationPresentInSet(newRelation, permanentRelations)) {
            version = GraphVersion.PERMANENT;
        } else {
            throw new IllegalStateException("The new relation is not present in either " +
                "mergedRelations or permanentRelations");
        }
        stage.add(new EdgeIntersectionRule(prefixIndex, direction, version, TypeAndPropertyKeyStore.
            getInstance().mapStringTypeToShort(newRelation.getRelationType())));
    }

    /**
     * @param queryRelationToCheck The {@link QueryRelation} to be searched.
     * @param queryRelations A set of {@link QueryRelation}s.
     *
     * @return {@code true} if {@code fromVariable} and {@code toVariable} match the corresponding
     * values of any of the {@link QueryRelation} present in {@code queryRelations}.
     */
    private boolean isRelationPresentInSet(QueryRelation queryRelationToCheck,
        Set<QueryRelation> queryRelations) {
        for (QueryRelation queryRelation : queryRelations) {
            if (QueryRelation.isSameAs(queryRelationToCheck, queryRelation)) {
                return true;
            }
        }
        return false;
    }

    private List<String> giveAllQueryRelationsVariableNames() {
        /*
         * Sets a name for all relations in the structured query. The names of the relations in
         * the StructuredQuery are set when a return statement is used. The goal of doing this is
         * to pass the names to the edgeId resolver in order to have all edgeIds resolved by the
         * time the MatchQueryOutput is at the sink.
         * Issue #37 once resolved would address an enhanced implementation.
         */
        List<String> orderedEdgeVariables = new ArrayList<>();

        int count = 0;
        for (QueryRelation queryRelation : structuredQuery.getQueryRelations()) {
            String relationName;
            if (null == queryRelation.getRelationName()) {
                relationName = count + "internalEdgeName";
                queryRelation.setRelationName(relationName);
                count++;
            } else {
                relationName = queryRelation.getRelationName();
            }
            orderedEdgeVariables.add(relationName);
            queryGraph.addToRelationMap(relationName, queryRelation);
        }
        return orderedEdgeVariables;
    }
}
