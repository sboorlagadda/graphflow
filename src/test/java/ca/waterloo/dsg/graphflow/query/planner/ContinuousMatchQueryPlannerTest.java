package ca.waterloo.dsg.graphflow.query.planner;

import ca.waterloo.dsg.graphflow.graph.Graph.Direction;
import ca.waterloo.dsg.graphflow.graph.Graph.GraphVersion;
import ca.waterloo.dsg.graphflow.graph.TypeStore;
import ca.waterloo.dsg.graphflow.outputsink.InMemoryOutputSink;
import ca.waterloo.dsg.graphflow.outputsink.OutputSink;
import ca.waterloo.dsg.graphflow.query.executors.GenericJoinIntersectionRule;
import ca.waterloo.dsg.graphflow.query.parser.StructuredQueryParser;
import ca.waterloo.dsg.graphflow.query.plans.ContinuousMatchQueryPlan;
import ca.waterloo.dsg.graphflow.query.plans.OneTimeMatchQueryPlan;
import ca.waterloo.dsg.graphflow.query.utils.StructuredQuery;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@code ContinuousMatchQueryPlanner}.
 */
public class ContinuousMatchQueryPlannerTest {

    private OutputSink outputSink = new InMemoryOutputSink();

    @Test
    public void testPlanSimpleTriangleQuery() throws Exception {
        // Create a continuous MATCH query plan for a simple triangle query with no types.
        StructuredQuery triangleStructuredQuery = new StructuredQueryParser().parse("MATCH " +
            "(a)->(b),(b)->(c),(c)->(a)");
        ContinuousMatchQueryPlan continuousMatchQueryPlanActual = (ContinuousMatchQueryPlan) new
            ContinuousMatchQueryPlanner(triangleStructuredQuery, outputSink).plan();

        // Create the continuous MATCH query plan manually.
        ContinuousMatchQueryPlan continuousMatchQueryPlanExpected = new ContinuousMatchQueryPlan(
            outputSink);
        OneTimeMatchQueryPlan query;
        List<GenericJoinIntersectionRule> stage;
        // Stage 0 for variable ordering "abc" where the diffRelation is "a"->"b" using the
        // {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 1 for variable ordering "abc" where the diffRelation is "a"->"b" using the
        // {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 2 for variable ordering "bca" where the diffRelation is "b"->"c" using the
        // {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS));
        query.addStage(stage);
        // Extend "bc" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 3 for variable ordering "bca" where the diffRelation is "b"->"c" using the
        // {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS));
        query.addStage(stage);
        // Extend "bc" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 4 for variable ordering "cab" where the diffRelation is "c"->"a" using the
        // {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS));
        query.addStage(stage);
        // Extend "ca" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 5 for variable ordering "cab" where the diffRelation is "c"->"a" using the
        // {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS));
        query.addStage(stage);
        // Extend "ca" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);

        Assert.assertTrue(ContinuousMatchQueryPlan.isSameAs(continuousMatchQueryPlanActual,
            continuousMatchQueryPlanExpected));
    }

    @Test
    public void testPlanTriangleQueryWithRelationTypes() throws Exception {
        // Initialize the {@code TypeStore} with types used in the MATCH query.
        short FOLLOWS_TYPE_ID = TypeStore.getInstance().getShortIdOrAddIfDoesNotExist("FOLLOWS");
        short LIKES_TYPE_ID = TypeStore.getInstance().getShortIdOrAddIfDoesNotExist("LIKES");
        // Create a continuous MATCH query plan for a complex triangle query with multiple
        // relations between variable having different edge types.
        StructuredQuery triangleStructuredQuery = new StructuredQueryParser().parse("MATCH " +
            "(a)-[:FOLLOWS]->(b),(a)-[:LIKES]->(b),(b)-[:LIKES]->(a),(b)->(c),(c)->(b)," +
            "(c)-[:FOLLOWS]->(a)");
        ContinuousMatchQueryPlan continuousMatchQueryPlanActual = (ContinuousMatchQueryPlan) new
            ContinuousMatchQueryPlanner(triangleStructuredQuery, outputSink).plan();

        // Create the continuous MATCH query plan manually.
        ContinuousMatchQueryPlan continuousMatchQueryPlanExpected = new ContinuousMatchQueryPlan(
            outputSink);
        OneTimeMatchQueryPlan query;
        List<GenericJoinIntersectionRule> stage;
        // Stage 0 for variable ordering "abc" where the diffRelation is "a"-[:FOLLOWS]->"b" using
        // the {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 1 for variable ordering "abc" where the diffRelation is "a"-[:FOLLOWS]->"b" using
        // the {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 2 for variable ordering "abc" where the diffRelation is "a"-[:LIKES]->"b" using
        // the {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 3 for variable ordering "abc" where the diffRelation is "a"-[:LIKES]->"b" using
        // the {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "a" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ab" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 4 for variable ordering "bac" where the diffRelation is "b"-[:LIKES]->"a" using
        // the {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ba" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 5 for variable ordering "bac" where the diffRelation is "b"-[:LIKES]->"a" using
        // the {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        // Extend "ba" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 6 for variable ordering "bca" where the diffRelation is "b"->"c" using the
        // {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        // Extend "bc" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 7 for variable ordering "bca" where the diffRelation is "b"->"c" using the
        // {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "b" to "c".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.PERMANENT,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        // Extend "bc" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 8 for variable ordering "cba" where the diffRelation is "c"->"b" using the
        // {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        // Extend "cb" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 9 for variable ordering "cba" where the diffRelation is "c"->"b" using the
        // {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        query.addStage(stage);
        // Extend "cb" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.PERMANENT,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 10 for variable ordering "cab" where the diffRelation is "c"-[:FOLLOWS]->"a" using
        // the {@code DIFF_PLUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_PLUS,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        // Extend "ca" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);
        // Stage 11 for variable ordering "cab" where the diffRelation is "c"-[:FOLLOWS]->"a" using
        // the {@code DIFF_MINUS} graph version.
        query = new OneTimeMatchQueryPlan();
        // Extend "c" to "a".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.DIFF_MINUS,
            FOLLOWS_TYPE_ID));
        query.addStage(stage);
        // Extend "ca" to "b".
        stage = new ArrayList<>();
        stage.add(new GenericJoinIntersectionRule(0, Direction.BACKWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(0, Direction.FORWARD, GraphVersion.MERGED,
            TypeStore.ANY_TYPE));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            FOLLOWS_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.FORWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        stage.add(new GenericJoinIntersectionRule(1, Direction.BACKWARD, GraphVersion.MERGED,
            LIKES_TYPE_ID));
        query.addStage(stage);
        continuousMatchQueryPlanExpected.addOneTimeMatchQueryPlan(query);

        Assert.assertTrue(ContinuousMatchQueryPlan.isSameAs(continuousMatchQueryPlanActual,
            continuousMatchQueryPlanExpected));
    }
}