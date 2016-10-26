package ca.waterloo.dsg.graphflow.query.planner;

import ca.waterloo.dsg.graphflow.query.StructuredQuery;
import ca.waterloo.dsg.graphflow.query.plans.CreateQueryPlan;
import ca.waterloo.dsg.graphflow.query.plans.IQueryPlan;

/**
 * Create a {@code IQueryPlan} for the CREATE operation.
 */
public class CreateQueryPlanner implements IQueryPlanner {

    @Override
    public IQueryPlan plan(StructuredQuery query) {
        return new CreateQueryPlan(query);
    }
}
