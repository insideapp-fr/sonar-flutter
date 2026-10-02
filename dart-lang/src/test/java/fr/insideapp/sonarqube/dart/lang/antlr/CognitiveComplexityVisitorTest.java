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

import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Lexer;
import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CognitiveComplexityVisitorTest {

    private int cognitive(String source) {
        final Dart2Lexer lexer = new Dart2Lexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        final Dart2Parser parser = new Dart2Parser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        final ParseTree root = parser.compilationUnit();

        CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        new CustomTreeVisitor(visitor).visit(root);
        return visitor.getComplexity();
    }

    @Test
    public void straightLineCodeIsZero() {
        assertThat(cognitive("int f(int a) => a + 1;\n")).isEqualTo(0);
    }

    @Test
    public void singleIfCostsOne() {
        assertThat(cognitive("void f(bool a) { if (a) { g(); } }\n")).isEqualTo(1);
    }

    @Test
    public void nestedIfAddsNesting() {
        // outer if: +1 (nesting 0); inner if: +1 +1 (nesting 1) = 3
        assertThat(cognitive("void f(bool a, bool b) { if (a) { if (b) { g(); } } }\n")).isEqualTo(3);
    }

    @Test
    public void elseAddsFlatIncrement() {
        // if +1, else +1 = 2
        assertThat(cognitive("void f(bool a) { if (a) { g(); } else { h(); } }\n")).isEqualTo(2);
    }

    @Test
    public void elseIfChainCostsFlatIncrement() {
        // if +1, each else-if +1, final else +1 = 4 (no nesting penalty along the chain)
        assertThat(cognitive(
            "void f(int a) { if (a == 1) { g(); } else if (a == 2) { h(); } else if (a == 3) { i(); } else { j(); } }\n"))
            .isEqualTo(4);
    }

    @Test
    public void elseIfChainInsideLambdaOnlyPaysLambdaNestingOnce() {
        // lambda raises nesting to 1: if +1+1; three else-if +1 each = 5
        assertThat(cognitive(
            "void f(List l) { l.forEach((x) { if (x == 1) { g(); } else if (x == 2) { h(); } else if (x == 3) { i(); } else if (x == 4) { j(); } }); }\n"))
            .isEqualTo(5);
    }

    @Test
    public void ifNestedInsideElseIfBranchGetsNestingFromOuterIfOnly() {
        // outer if +1, else-if +1 (via else), inner if +1+1 (nesting 1) = 4
        assertThat(cognitive(
            "void f(int a, bool c) { if (a == 1) { g(); } else if (a == 2) { if (c) { h(); } } }\n"))
            .isEqualTo(4);
    }

    @Test
    public void ifInsideElseBlockStillNests() {
        // outer if +1, else +1, inner if inside block +1+1 (nesting 1) = 4
        assertThat(cognitive(
            "void f(bool a, bool b) { if (a) { g(); } else { if (b) { h(); } } }\n"))
            .isEqualTo(4);
    }

    @Test
    public void loopWithNestedConditionAddsNesting() {
        // for: +1 (nesting 0); inner if: +1 +1 = 2 -> total 3
        assertThat(cognitive("void f(List l) { for (final x in l) { if (x == 1) { g(); } } }\n")).isEqualTo(3);
    }

    @Test
    public void booleanOperatorSequences() {
        // if +1, one && sequence +1, one || sequence +1 = 3
        assertThat(cognitive("void f(bool a, bool b, bool c) { if (a && b || c) { g(); } }\n")).isEqualTo(3);
    }

    @Test
    public void ternaryCostsOne() {
        assertThat(cognitive("int f(bool a) => a ? 1 : 2;\n")).isEqualTo(1);
    }

    @Test
    public void catchClauseCostsOne() {
        assertThat(cognitive("void f() { try { g(); } catch (e) { h(); } }\n")).isEqualTo(1);
    }

    @Test
    public void lambdaAddsNestingForInnerControlFlow() {
        // lambda increases nesting; inner if: +1 +1 = 2
        assertThat(cognitive("void f(List l) { l.forEach((x) { if (x == 1) { g(); } }); }\n")).isEqualTo(2);
    }

    private java.util.List<int[]> perFunction(String source) {
        final Dart2Lexer lexer = new Dart2Lexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        final Dart2Parser parser = new Dart2Parser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        return new CognitiveComplexityVisitor().perFunctionComplexities(parser.compilationUnit());
    }

    @Test
    public void perFunctionReportsEachTopLevelFunction() {
        java.util.List<int[]> result = perFunction(
            "void simple() { g(); }\n" +
            "void complex(bool a, bool b) { if (a) { if (b) { g(); } } }\n");
        assertThat(result).hasSize(2);
        // simple() -> 0, complex() -> 3 (if +1, nested if +2)
        assertThat(result.get(0)[1]).isEqualTo(0);
        assertThat(result.get(1)[1]).isEqualTo(3);
    }

    @Test
    public void perFunctionCoversMethodsInsideClasses() {
        java.util.List<int[]> result = perFunction(
            "class C {\n" +
            "  void m(bool a) { if (a) { g(); } }\n" +
            "}\n");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)[1]).isEqualTo(1);
    }
}
