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
package fr.insideapp.sonarqube.dart.lang;

import java.io.IOException;
import java.nio.charset.Charset;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

import fr.insideapp.sonarqube.dart.lang.antlr.AntlrContext;
import fr.insideapp.sonarqube.dart.lang.antlr.CustomTreeVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.CognitiveComplexityVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.CyclomaticComplexityVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.HighlighterVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.ParseTreeItemVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.SourceLinesVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.StructureMetricsVisitor;
import fr.insideapp.sonarqube.dart.lang.antlr.SymbolTableVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

public class DartSensor implements Sensor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DartSensor.class);

    @Override
    public void describe(@Nonnull SensorDescriptor sensorDescriptor) {
        sensorDescriptor
                .onlyOnLanguage(Dart.KEY)
                .name("Dart sensor")
                .onlyOnFileType(InputFile.Type.MAIN);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void execute(SensorContext sensorContext) {
        FilePredicate hasDart = sensorContext.fileSystem().predicates().hasLanguage(Dart.KEY);
        FilePredicate isMain = sensorContext.fileSystem().predicates().hasType(InputFile.Type.MAIN);
        FilePredicate isTest = sensorContext.fileSystem().predicates().hasType(InputFile.Type.TEST);
        FilePredicate dartAndMain = sensorContext.fileSystem().predicates().and(hasDart, isMain);
        FilePredicate dartAndTest = sensorContext.fileSystem().predicates().and(hasDart, isTest);
        final Charset charset = sensorContext.fileSystem().encoding();

        final org.sonar.api.batch.rule.ActiveRule cognitiveRule = sensorContext.activeRules()
                .find(fr.insideapp.sonarqube.dart.lang.issues.DartLanguageRulesDefinition.COGNITIVE_COMPLEXITY_RULE);
        final int cognitiveThreshold = cognitiveThreshold(cognitiveRule);

        for (InputFile inf : sensorContext.fileSystem().inputFiles(dartAndMain)) {

            // Visit source files
            try {
                final AntlrContext antlrContext = AntlrContext.fromInputFile(inf, charset);
                ParseTreeItemVisitor visitor = new CustomTreeVisitor(new HighlighterVisitor(),
                        new SourceLinesVisitor(), new CyclomaticComplexityVisitor(),
                        new CognitiveComplexityVisitor(), new SymbolTableVisitor(),
                        new StructureMetricsVisitor());
                visitor.fillContext(sensorContext, antlrContext);

                if (cognitiveRule != null) {
                    reportHighCognitiveComplexity(sensorContext, inf, antlrContext, cognitiveRule, cognitiveThreshold);
                }
            } catch (IOException e) {
                LOGGER.warn("Unexpected error while analyzing file " + inf.filename(), e);
            }

        }

        for (InputFile inf : sensorContext.fileSystem().inputFiles(dartAndTest)) {

            // Visit test files (for syntax highlighting only)
            try {
                final AntlrContext antlrContext = AntlrContext.fromInputFile(inf, charset);
                ParseTreeItemVisitor visitor = new CustomTreeVisitor(new HighlighterVisitor(),
                        new SymbolTableVisitor());
                visitor.fillContext(sensorContext, antlrContext);
            } catch (IOException e) {
                LOGGER.warn("Unexpected error while analyzing file " + inf.filename(), e);
            }

        }

    }

    private int cognitiveThreshold(org.sonar.api.batch.rule.ActiveRule rule) {
        if (rule != null) {
            final String value = rule.param(
                    fr.insideapp.sonarqube.dart.lang.issues.DartLanguageRulesDefinition.THRESHOLD_PARAM);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    // fall through to default
                }
            }
        }
        return fr.insideapp.sonarqube.dart.lang.issues.DartLanguageRulesDefinition.DEFAULT_THRESHOLD;
    }

    private void reportHighCognitiveComplexity(SensorContext context, InputFile file, AntlrContext antlrContext,
                                               org.sonar.api.batch.rule.ActiveRule rule, int threshold) {
        final CognitiveComplexityVisitor visitor = new CognitiveComplexityVisitor();
        for (int[] fn : visitor.perFunctionComplexities(antlrContext.getRoot())) {
            final int line = fn[0];
            final int complexity = fn[1];
            if (complexity > threshold) {
                final org.sonar.api.batch.sensor.issue.NewIssue issue = context.newIssue().forRule(rule.ruleKey());
                issue.at(issue.newLocation().on(file).at(file.selectLine(line)).message(String.format(
                        "Refactor this function to reduce its Cognitive Complexity from %d to the %d allowed.",
                        complexity, threshold)));
                issue.save();
            }
        }
    }
}
