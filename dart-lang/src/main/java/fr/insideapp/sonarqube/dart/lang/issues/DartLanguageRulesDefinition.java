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
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;

/** Plugin-native language rules computed from the ANTLR parse tree. */
public class DartLanguageRulesDefinition implements RulesDefinition {

    public static final String REPOSITORY_KEY = "dart";
    public static final String REPOSITORY_NAME = "Dart";

    public static final String COGNITIVE_COMPLEXITY = "function_cognitive_complexity";
    public static final String THRESHOLD_PARAM = "threshold";
    public static final int DEFAULT_THRESHOLD = 15;

    public static final RuleKey COGNITIVE_COMPLEXITY_RULE = RuleKey.of(REPOSITORY_KEY, COGNITIVE_COMPLEXITY);

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(REPOSITORY_KEY, Dart.KEY).setName(REPOSITORY_NAME);

        NewRule rule = repository.createRule(COGNITIVE_COMPLEXITY)
                .setName("Cognitive Complexity of functions should not be too high")
                .setType(RuleType.CODE_SMELL)
                .setActivatedByDefault(true)
                .setHtmlDescription(getClass().getResource("/dart/function_cognitive_complexity.html"));
        rule.setDebtRemediationFunction(rule.debtRemediationFunctions().linear("5min"));
        rule.createParam(THRESHOLD_PARAM)
                .setName("Threshold")
                .setDescription("The maximum authorized Cognitive Complexity of a function.")
                .setType(RuleParamType.INTEGER)
                .setDefaultValue(String.valueOf(DEFAULT_THRESHOLD));

        repository.done();
    }
}
