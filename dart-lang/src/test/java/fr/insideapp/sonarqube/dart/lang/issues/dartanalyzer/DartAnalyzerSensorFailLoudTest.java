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
package fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer;

import fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer.executable.AnalyzerExecutable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DartAnalyzerSensorFailLoudTest {

    private AnalyzerOutput output(int exitValue, String content) {
        return new AnalyzerOutput(AnalyzerOutput.Mode.LEGACY, AnalyzerExecutable.Mode.FLUTTER, content, exitValue);
    }

    @Test
    public void cleanRunWithNoIssuesIsFine() {
        DartAnalyzerSensor.ensureUsableOutput(output(0, "No issues found!"), 0);
    }

    @Test
    public void nonZeroExitWithIssuesIsFine() {
        // dart/flutter analyze exit non-zero when they find issues - that is a successful analysis
        DartAnalyzerSensor.ensureUsableOutput(output(1, "info - unused_import"), 5);
    }

    @Test
    public void nonZeroExitWithoutAnyParsedIssueFailsLoudly() {
        // e.g. flutter analyze failing during implicit pub get: error text goes
        // to stdout, exit code is non-zero and no issue can be parsed from it
        assertThatThrownBy(() -> DartAnalyzerSensor.ensureUsableOutput(
                output(1, "The current Dart SDK version is..... pub get failed"), 0))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit");
    }

    @Test
    public void analyzerOutputExposesExitValue() {
        assertThat(output(3, "x").getExitValue()).isEqualTo(3);
    }
}
