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

import fr.insideapp.sonarqube.dart.lang.Dart;
import fr.insideapp.sonarqube.dart.lang.PubSpec;
import fr.insideapp.sonarqube.dart.lang.PubSpecParser;
import fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer.executable.AnalyzerExecutable;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.rule.RuleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static java.util.Arrays.asList;

public class DartAnalyzerSensor implements Sensor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DartAnalyzerSensor.class);

    public static final String ANALYZER_MODE = "sonar.dart.analyzer.mode";
    public static final List<AnalyzerExecutable.Mode> ANALYZER_MODE_OPTIONS = asList(AnalyzerExecutable.Mode.values());

    public static final String ANALYZER_OPTIONS_OVERRIDE = "sonar.dart.analyzer.options.override";
    public static final String ANALYZER_OPTIONS_OVERRIDE_DEFAULT = "true";

    public static final String ANALYZER_REPORT_PATH = "sonar.dart.analyzer.report.path";

    public static final String ANALYZER_OUTPUT_MODE = "sonar.dart.analyzer.report.mode";
    public static final List<AnalyzerOutput.Mode> ANALYZER_OUTPUT_MODE_OPTIONS = asList(AnalyzerOutput.Mode.values());

    @Override
    public void describe(SensorDescriptor sensorDescriptor) {
        sensorDescriptor.onlyOnLanguage(Dart.KEY).name("Dart analysis sensor").onlyOnFileType(Type.MAIN);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void execute(SensorContext sensorContext) {
        try {
            final PubSpec pubSpec = PubSpecParser.parse(sensorContext);
            final AnalyzerOutput output = AnalyzerExecutable.create(sensorContext, pubSpec).analyze();

            DartAnalyzerReportParser parser = new FlutterAnalyzerReportParser();

            if (!output.getAnalyzerMode().equals(AnalyzerExecutable.Mode.FLUTTER)) {
                parser = output.getMode().equals(AnalyzerOutput.Mode.MACHINE)
                        ? new DartAnalyzerMachineReportParser() : new DartAnalyzerLegacyReportParser();
            }

            final List<DartAnalyzerReportIssue> issues = parser.parse(output.getContent());

            ensureUsableOutput(output, issues.size());

            LOGGER.info("Recording {} issues", issues.size());

            recordIssues(sensorContext, issues);
        } catch (IOException e) {
            LOGGER.error("Analysis failed", e);
            throw new IllegalStateException("Dart analyzer execution failed, see the log above for details", e);
        }
    }

    /**
     * dart/flutter analyze exit with a non-zero status when they find issues,
     * which is a successful analysis. A non-zero exit status combined with an
     * output no issue could be parsed from means the analyzer itself failed
     * (e.g. an implicit 'pub get' failure) and must not be reported as a
     * successful analysis with zero issues.
     */
    static void ensureUsableOutput(AnalyzerOutput output, int issueCount) {
        if (issueCount == 0 && output.getExitValue() != 0) {
            throw new IllegalStateException(String.format(
                    "Analyzer exited with status %d and no issue could be parsed from its output. " +
                    "The analyzer likely failed to run. Output was:%n%s",
                    output.getExitValue(), output.getContent()));
        }
    }

    private void recordIssues(SensorContext sensorContext, List<DartAnalyzerReportIssue> issues) {
        issues.forEach(issue -> {
            File file = sensorContext.fileSystem().resolvePath(issue.getFilePath());
            LOGGER.debug("Recording issue for {}", file.getAbsolutePath());

            FilePredicate fp = sensorContext.fileSystem().predicates().hasAbsolutePath(file.getAbsolutePath());
            if (!sensorContext.fileSystem().hasFiles(fp)) {
                LOGGER.warn("File not included in SonarQube {}", file.getAbsoluteFile());
            } else {
                final InputFile inputFile = Objects.requireNonNull(sensorContext.fileSystem().inputFile(fp));
                final org.sonar.api.batch.sensor.issue.NewIssue newIssue = sensorContext.newIssue()
                        .forRule(RuleKey.of(DartAnalyzerRulesDefinition.REPOSITORY_KEY, issue.getRuleId().toLowerCase(Locale.ROOT)));
                newIssue.at(issue.toNewIssueLocationFor(newIssue, inputFile))
                        .save();
            }
        });
    }
}
