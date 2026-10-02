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

public class StructureMetricsVisitorTest {

    private StructureMetricsVisitor visit(String source) {
        final Dart2Lexer lexer = new Dart2Lexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        final Dart2Parser parser = new Dart2Parser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        final ParseTree root = parser.compilationUnit();
        StructureMetricsVisitor visitor = new StructureMetricsVisitor();
        new CustomTreeVisitor(visitor).visit(root);
        return visitor;
    }

    @Test
    public void countsClassesFunctionsAndStatements() {
        StructureMetricsVisitor v = visit(
            "class A {\n" +
            "  void m() { final x = 1; print(x); }\n" +
            "}\n" +
            "void top() { g(); }\n");
        assertThat(v.getClasses()).isEqualTo(1);
        assertThat(v.getFunctions()).isEqualTo(2); // m() and top()
        assertThat(v.getStatements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    public void emptyFileIsAllZero() {
        StructureMetricsVisitor v = visit("");
        assertThat(v.getClasses()).isEqualTo(0);
        assertThat(v.getFunctions()).isEqualTo(0);
        assertThat(v.getStatements()).isEqualTo(0);
    }
}
