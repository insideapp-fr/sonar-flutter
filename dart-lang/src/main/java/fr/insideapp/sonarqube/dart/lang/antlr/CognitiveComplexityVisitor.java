/*
 * SonarQube Flutter Plugin - Enables analysis of Dart and Flutter projects into SonarQube.
 * Copyright © 2020 inside|app (contact@insideapp.fr)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.insideapp.sonarqube.dart.lang.antlr;

import static java.lang.String.format;

import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes Cognitive Complexity (SonarSource specification, adapted to the
 * Dart2 grammar). Structures that break the linear flow cost +1, and a nesting
 * penalty equal to the current nesting depth is added for nested control flow.
 *
 * <p>The measure needs nesting context, so instead of the post-order
 * {@link #apply(ParseTree)} accumulation used by other visitors, the whole
 * computation is done in {@link #fillContext} through a dedicated pre-order walk.
 */
public class CognitiveComplexityVisitor implements ParseTreeItemVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitiveComplexityVisitor.class);

    private int complexity = 0;
    private boolean computed = false;

    @Override
    public void apply(ParseTree tree) {
        // Cognitive complexity is nesting-aware and cannot be computed from the
        // flat post-order apply() model. apply() is called for every node, so we
        // trigger the dedicated pre-order walk once, when the compilation unit
        // root is reached (post-order visits it last).
        if (!computed && Dart2Parser.CompilationUnitContext.class.equals(tree.getClass())) {
            this.complexity = computeFrom(tree, 0);
            this.computed = true;
        }
    }

    public int getComplexity() {
        return complexity;
    }

    /**
     * Cognitive complexity of each outermost function/method body, as
     * {@code int[]{line, complexity}}. Nested local functions and lambdas are
     * counted within their enclosing function, matching the "cognitive
     * complexity of a function" rule of mature analyzers.
     */
    public java.util.List<int[]> perFunctionComplexities(ParseTree root) {
        final java.util.List<int[]> result = new java.util.ArrayList<>();
        collectFunctions(root, result);
        return result;
    }

    private void collectFunctions(ParseTree tree, java.util.List<int[]> result) {
        if (Dart2Parser.FunctionBodyContext.class.equals(tree.getClass())) {
            final int line = ((org.antlr.v4.runtime.ParserRuleContext) tree).getStart().getLine();
            result.add(new int[]{line, computeFrom(tree, 0)});
            return; // do not descend: nested bodies are counted within this one
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collectFunctions(tree.getChild(i), result);
        }
    }

    private int computeFrom(ParseTree tree, int nesting) {
        int total = 0;
        final Class<? extends ParseTree> classz = tree.getClass();

        // An `else if` costs a flat +1 (charged by the parent's else increment
        // below): no base/nesting increment of its own, and no extra nesting
        // level for the rest of the chain.
        final boolean elseIf = isElseIf(tree, classz);
        final boolean nestingStructure = !elseIf && isNestingStructure(classz, tree);
        if (!elseIf && isBaseIncrement(classz, tree)) {
            total += 1 + nesting;
        }
        if (Dart2Parser.IfStatementContext.class.equals(classz) && hasElse(tree)) {
            total += 1;
        }
        if (isBooleanSequence(classz, tree)) {
            total += 1;
        }

        final int childNesting = nesting + (nestingStructure ? 1 : 0);
        for (int i = 0; i < tree.getChildCount(); i++) {
            total += computeFrom(tree.getChild(i), childNesting);
        }
        return total;
    }

    private static boolean isBaseIncrement(Class<? extends ParseTree> classz, ParseTree tree) {
        return Dart2Parser.IfStatementContext.class.equals(classz)
                || Dart2Parser.ForStatementContext.class.equals(classz)
                || Dart2Parser.WhileStatementContext.class.equals(classz)
                || Dart2Parser.DoStatementContext.class.equals(classz)
                || Dart2Parser.SwitchStatementContext.class.equals(classz)
                || Dart2Parser.SwitchExpressionContext.class.equals(classz)
                || Dart2Parser.OnPartContext.class.equals(classz)
                || isTernary(classz, tree);
    }

    private static boolean isNestingStructure(Class<? extends ParseTree> classz, ParseTree tree) {
        return isBaseIncrement(classz, tree)
                || Dart2Parser.FunctionExpressionContext.class.equals(classz);
    }

    private static boolean isTernary(Class<? extends ParseTree> classz, ParseTree tree) {
        // conditionalExpression: ifNullExpression ('?' ... ':' ...)?  -> ternary only when the optional part is present
        return Dart2Parser.ConditionalExpressionContext.class.equals(classz) && tree.getChildCount() > 1;
    }

    private static boolean isBooleanSequence(Class<? extends ParseTree> classz, ParseTree tree) {
        // logicalAndExpression / logicalOrExpression have more than one child when they contain && / ||
        return (Dart2Parser.LogicalAndExpressionContext.class.equals(classz)
                || Dart2Parser.LogicalOrExpressionContext.class.equals(classz))
                && tree.getChildCount() > 1;
    }

    /**
     * The Dart2 grammar has no dedicated else-if node: `else if` parses as
     * ifStatement -> 'else' statement -> nonLabledStatment -> ifStatement.
     * Detects an IfStatementContext sitting directly in the else position of
     * an enclosing if (an `else` whose statement is a block is NOT an else-if).
     */
    private static boolean isElseIf(ParseTree tree, Class<? extends ParseTree> classz) {
        if (!Dart2Parser.IfStatementContext.class.equals(classz)) {
            return false;
        }
        final ParseTree nonLabled = tree.getParent();
        if (nonLabled == null || !Dart2Parser.NonLabledStatmentContext.class.equals(nonLabled.getClass())) {
            return false;
        }
        final ParseTree statement = nonLabled.getParent();
        if (statement == null || !Dart2Parser.StatementContext.class.equals(statement.getClass())) {
            return false;
        }
        final ParseTree parentIf = statement.getParent();
        if (parentIf == null || !Dart2Parser.IfStatementContext.class.equals(parentIf.getClass())) {
            return false;
        }
        // the statement must be the one right after the 'else' terminal
        for (int i = 1; i < parentIf.getChildCount(); i++) {
            if (parentIf.getChild(i) == statement) {
                final ParseTree previous = parentIf.getChild(i - 1);
                return previous instanceof TerminalNode && "else".equals(previous.getText());
            }
        }
        return false;
    }

    private static boolean hasElse(ParseTree tree) {
        for (int i = 0; i < tree.getChildCount(); i++) {
            final ParseTree child = tree.getChild(i);
            if (child instanceof TerminalNode && "else".equals(child.getText())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void fillContext(SensorContext context, AntlrContext antlrContext) {
        final InputFile file = antlrContext.getFile();
        synchronized (CognitiveComplexityVisitor.class) {
            try {
                context.<Integer>newMeasure().on(file).forMetric(CoreMetrics.COGNITIVE_COMPLEXITY)
                        .withValue(complexity).save();
            } catch (final Throwable e) {
                LOGGER.warn(format("Unexpected error adding cognitive complexity measure on file %s", file.key()), e);
            }
        }
    }
}
