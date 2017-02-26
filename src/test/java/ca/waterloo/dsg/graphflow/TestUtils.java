package ca.waterloo.dsg.graphflow;

import ca.waterloo.dsg.graphflow.graph.EdgeStore;
import ca.waterloo.dsg.graphflow.graph.Graph;
import ca.waterloo.dsg.graphflow.graph.TypeAndPropertyKeyStore;
import ca.waterloo.dsg.graphflow.graph.VertexPropertyStore;
import ca.waterloo.dsg.graphflow.query.operator.AbstractDBOperator;
import ca.waterloo.dsg.graphflow.query.operator.InMemoryOutputSink;
import ca.waterloo.dsg.graphflow.query.parser.StructuredQueryParser;
import ca.waterloo.dsg.graphflow.query.planner.CreateQueryPlanner;
import ca.waterloo.dsg.graphflow.query.plans.CreateQueryPlan;
import ca.waterloo.dsg.graphflow.query.structuredquery.QueryRelation;
import ca.waterloo.dsg.graphflow.query.structuredquery.StructuredQuery;
import ca.waterloo.dsg.graphflow.util.DataType;
import org.antlr.v4.runtime.misc.Pair;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Provides utility functions for tests.
 */
public class TestUtils {

    /**
     * Creates and returns a graph initialized with the given {@code edges}, {@code edgeTypes} and
     * {@code vertexTypes}.
     *
     * @param edges The edges {e=(u,v)} of the graph.
     * @param edgeTypes The type of each edge e.
     * @param vertexTypes The types {@code (t1, t2)} where t1 is the type of source vertex u and t2
     * is the type of destination vertex v.
     * @return Graph The initialized graph.
     */
    public static Graph initializeGraphPermanently(int[][] edges, short[] edgeTypes,
        short[][] vertexTypes) {
        Graph graph = initializeGraphTemporarily(edges, edgeTypes, vertexTypes);
        graph.finalizeChanges();
        return graph;
    }

    /**
     * Creates and returns a graph with with the given {@code edges}, {@code edgeTypes} and {@code
     * vertexTypes} added temporarily.
     *
     * @param edges The edges {e=(u,v)} of the graph.
     * @param edgeTypes The type of each edge e.
     * @param vertexTypes The types {@code (t1, t2)} where t1 is the type of source vertex u and t2
     * is the type of destination vertex v.
     * @return Graph The graph initialized with temporary edges.
     */
    public static Graph initializeGraphTemporarily(int[][] edges, short[] edgeTypes,
        short[][] vertexTypes) {
        Graph graph = Graph.getInstance();
        for (int i = 0; i < edges.length; i++) {
            graph.addEdgeTemporarily(edges[i][0], edges[i][1], vertexTypes[i][0],
                vertexTypes[i][1], null /* no fromVertex properties */, null /* no toVertex
                properties */, edgeTypes[i], null /* no edge properties */);
        }
        return graph;
    }

    /**
     * Adds a set of edges to the given {@code graph} by executing the given {@code createQuery}.
     *
     * @param graph The {@link Graph} instance to which the edges should be added.
     * @param createQuery The {@code String} create query to be executed.
     */
    public static void createEdgesPermanently(Graph graph, String createQuery) {
        createEdgesTemporarily(graph, createQuery);
        graph.finalizeChanges();
    }

    /**
     * Adds a set of edges to the given {@code graph} temporarily by executing the given {@code
     * createQuery}.
     *
     * @param graph The {@link Graph} instance to which the edges should be added.
     * @param createQuery The {@code String} create query to be executed.
     */
    public static void createEdgesTemporarily(Graph graph, String createQuery) {
        StructuredQuery structuredQuery = new StructuredQueryParser().parse(createQuery);
        for (QueryRelation queryRelation : structuredQuery.getQueryRelations()) {
            int fromVertex = Integer.parseInt(queryRelation.getFromQueryVariable().
                getVariableName());
            int toVertex = Integer.parseInt(queryRelation.getToQueryVariable().getVariableName());
            // Insert the types into the {@code TypeStore} if they do not already exist, and
            // get their {@code short} IDs. An exception in the above {@code parseInt()} calls
            // will prevent the insertion of any new type to the {@code TypeStore}.
            short fromVertexTypeId = TypeAndPropertyKeyStore.getInstance().
                mapStringTypeToShortOrInsert(queryRelation.getFromQueryVariable().
                    getVariableType());
            short toVertexTypeId = TypeAndPropertyKeyStore.getInstance().
                mapStringTypeToShortOrInsert(queryRelation.getToQueryVariable().getVariableType());
            short edgeTypeId = TypeAndPropertyKeyStore.getInstance().
                mapStringTypeToShortOrInsert(queryRelation.getRelationType());
            // Add the new edge to the graph.
            graph.addEdgeTemporarily(fromVertex, toVertex, fromVertexTypeId, toVertexTypeId, null
                /* no fromVertex properties */, null /* no toVertex properties */, edgeTypeId,
                null /* no edge properties */);
        }
    }

    /**
     * Deletes a set of edges from the given {@code graph} permanently by executing the given {@code
     * deleteQuery}.
     *
     * @param graph The {@link Graph} instance from which the edges should be deleted.
     * @param deleteQuery The {@code String} delete query to be executed.
     */
    public static void deleteEdgesPermanently(Graph graph, String deleteQuery) {
        deleteEdgesTemporarily(graph, deleteQuery);
        graph.finalizeChanges();
    }

    /**
     * Deletes a set of edges from the given {@code graph} temporarily by executing the given {@code
     * deleteQuery}.
     *
     * @param graph The {@link Graph} instance from which the edges should be deleted.
     * @param deleteQuery The {@code String} delete query to be executed.
     */
    public static void deleteEdgesTemporarily(Graph graph, String deleteQuery) {
        StructuredQuery structuredQuery = new StructuredQueryParser().parse(deleteQuery);
        for (QueryRelation queryRelation : structuredQuery.getQueryRelations()) {
            graph.deleteEdgeTemporarily(
                Integer.parseInt(queryRelation.getFromQueryVariable().getVariableName()),
                Integer.parseInt(queryRelation.getToQueryVariable().getVariableName()),
                TypeAndPropertyKeyStore.getInstance().mapStringTypeToShort(
                    queryRelation.getRelationType()));
        }
    }

    public static void initializeGraphPermanentlyWithProperties(String createQuery) {
        StructuredQuery structuredQuery = new StructuredQueryParser().parse(createQuery);
        AbstractDBOperator outputSink = new InMemoryOutputSink();
        ((CreateQueryPlan) new CreateQueryPlanner(structuredQuery).plan()).execute(Graph
            .getInstance(), outputSink);
    }
}
