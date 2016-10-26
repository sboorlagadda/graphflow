package ca.waterloo.dsg.graphflow.query.parser;

import ca.waterloo.dsg.graphflow.grammar.GraphflowBaseVisitor;
import ca.waterloo.dsg.graphflow.grammar.GraphflowParser;
import ca.waterloo.dsg.graphflow.query.StructuredQuery;
import ca.waterloo.dsg.graphflow.query.StructuredQueryEdge;

/**
 * This class implements the ANTLR4 methods used to traverse the parse tree.
 * Query structure is encapsulated within a {@code StructuredQuery} object.
 */
public class GraphflowVisitor extends GraphflowBaseVisitor<StructuredQuery> {

    @Override
    public StructuredQuery visitGraphflow(GraphflowParser.GraphflowContext ctx) {
        return visit(ctx.statement());
    }

    @Override
    public StructuredQuery visitMatchPattern(GraphflowParser.MatchPatternContext ctx) {
        StructuredQuery structuredQuery = visit(ctx.variableExpression(0));
        structuredQuery.setOperation(StructuredQuery.Operation.MATCH);
        for (int i = 1; i < ctx.variableExpression().size(); i++) {
            StructuredQuery tmpedge = visit(ctx.variableExpression(i));
            structuredQuery.addEdge(tmpedge.getStructuredQueryEdges().get(0));
        }
        return structuredQuery;
    }

    @Override
    public StructuredQuery visitDeletePattern(GraphflowParser.DeletePatternContext ctx) {
        StructuredQuery structuredQuery = visit(ctx.digitsExpression(0));
        structuredQuery.setOperation(StructuredQuery.Operation.DELETE);
        for (int i = 1; i < ctx.digitsExpression().size(); i++) {
            StructuredQuery tmpedge = visit(ctx.digitsExpression(i));
            structuredQuery.addEdge(tmpedge.getStructuredQueryEdges().get(0));
        }
        return structuredQuery;
    }

    @Override
    public StructuredQuery visitCreatePattern(GraphflowParser.CreatePatternContext ctx) {
        StructuredQuery structuredQuery = visit(ctx.digitsExpression(0));
        structuredQuery.setOperation(StructuredQuery.Operation.CREATE);
        for (int i = 1; i < ctx.digitsExpression().size(); i++) {
            StructuredQuery tmpedge = visit(ctx.digitsExpression(i));
            structuredQuery.addEdge(tmpedge.getStructuredQueryEdges().get(0));
        }
        return structuredQuery;
    }

    @Override
    public StructuredQuery visitDigitsExpression(GraphflowParser.DigitsExpressionContext ctx) {
        StructuredQuery structuredQuery = new StructuredQuery();
        StructuredQueryEdge structuredQueryEdge = new StructuredQueryEdge(ctx.leftDigit().getText(),
            ctx.rightDigit().getText());
        structuredQuery.addEdge(structuredQueryEdge);
        return structuredQuery;
    }

    @Override
    public StructuredQuery visitVariableExpression(GraphflowParser.VariableExpressionContext ctx) {
        StructuredQuery structuredQuery = new StructuredQuery();
        StructuredQueryEdge structuredQueryEdge = new StructuredQueryEdge(
            ctx.leftVariable().getText(), ctx.rightVariable().getText());
        structuredQuery.addEdge(structuredQueryEdge);
        return structuredQuery;
    }
}
