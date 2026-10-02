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
package fr.insideapp.sonarqube.dart.lang.issues;

import fr.insideapp.sonarqube.dart.lang.Dart;
import fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer.DartAnalyzerRulesDefinition;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DartProfile implements BuiltInQualityProfilesDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(DartProfile.class);

    @Override
    public void define(BuiltInQualityProfilesDefinition.Context context) {

        NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(DartAnalyzerRulesDefinition.REPOSITORY_KEY, Dart.KEY);
        profile.setDefault(true);
        RepositoryRuleParser repositoryRuleParser = new RepositoryRuleParser();

        // dartanalyzer rules
        try {
            List<RepositoryRule> rules = repositoryRuleParser.parse(DartAnalyzerRulesDefinition.RULES_FILE);
            for (RepositoryRule r: rules) {

                if ( r.name == null || r.severity == null || r.type == null || r.description == null) {
                    LOGGER.warn(String.format("Cannot add %s rule to dartanalyzer profile, rule data is missing in rules.json", r.key));
                } else {
                    if (r.active) {
                        NewBuiltInActiveRule rule = profile.activateRule("dartanalyzer", r.key);
                        rule.overrideSeverity(r.severity.name());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load dartanalyzer rules", e);
        }

        // Security hotspots and vulnerabilities (plugin-native)
        final String securityRepo = fr.insideapp.sonarqube.dart.lang.issues.security.DartSecurityRulesDefinition.REPOSITORY_KEY;
        for (String securityRuleKey : fr.insideapp.sonarqube.dart.lang.issues.security.DartSecurityScanner.RULE_KEYS) {
            profile.activateRule(securityRepo, securityRuleKey);
        }
        for (String vulnRuleKey : fr.insideapp.sonarqube.dart.lang.issues.security.DartSecurityScanner.VULNERABILITY_RULE_KEYS) {
            profile.activateRule(securityRepo, vulnRuleKey);
        }

        // Plugin-native language rules
        profile.activateRule(DartLanguageRulesDefinition.REPOSITORY_KEY, DartLanguageRulesDefinition.COGNITIVE_COMPLEXITY);

        profile.done();
    }
}
