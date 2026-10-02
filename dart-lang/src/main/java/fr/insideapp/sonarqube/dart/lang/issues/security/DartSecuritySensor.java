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
package fr.insideapp.sonarqube.dart.lang.issues.security;

import fr.insideapp.sonarqube.dart.lang.Dart;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.List;

public class DartSecuritySensor implements Sensor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DartSecuritySensor.class);

    private final DartSecurityScanner scanner = new DartSecurityScanner();

    @Override
    public void describe(SensorDescriptor sensorDescriptor) {
        sensorDescriptor.onlyOnLanguage(Dart.KEY).name("Dart security sensor");
    }

    @Override
    @ParametersAreNonnullByDefault
    public void execute(SensorContext sensorContext) {
        int total = 0;
        for (InputFile file : sensorContext.fileSystem().inputFiles(
                sensorContext.fileSystem().predicates().hasLanguage(Dart.KEY))) {
            try {
                final List<SecurityFinding> findings = scanner.scan(file.contents());
                for (SecurityFinding finding : findings) {
                    record(sensorContext, file, finding);
                    total++;
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to run security scan on " + file.filename(), e);
            }
        }
        LOGGER.info("Recording {} security hotspots", total);
    }

    private void record(SensorContext sensorContext, InputFile file, SecurityFinding finding) {
        final NewIssue issue = sensorContext.newIssue()
                .forRule(RuleKey.of(DartSecurityRulesDefinition.REPOSITORY_KEY, finding.getRuleKey()));
        final NewIssueLocation location = issue.newLocation()
                .on(file)
                .at(file.selectLine(finding.getLine()))
                .message(finding.getMessage());
        issue.at(location).save();
    }
}
