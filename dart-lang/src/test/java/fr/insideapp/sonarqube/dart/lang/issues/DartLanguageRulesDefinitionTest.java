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

import org.junit.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class DartLanguageRulesDefinitionTest {

    @Test
    public void define() {
        DartLanguageRulesDefinition def = new DartLanguageRulesDefinition();
        RulesDefinition.Context context = new RulesDefinition.Context();
        def.define(context);

        RulesDefinition.Repository repo = context.repository(DartLanguageRulesDefinition.REPOSITORY_KEY);
        assertThat(repo).isNotNull();
        assertThat(repo.language()).isEqualTo("dart");

        RulesDefinition.Rule rule = repo.rule(DartLanguageRulesDefinition.COGNITIVE_COMPLEXITY);
        assertThat(rule).isNotNull();
        assertThat(rule.type()).isEqualTo(RuleType.CODE_SMELL);
        assertThat(rule.activatedByDefault()).isTrue();
        assertThat(rule.param("threshold")).isNotNull();
        assertThat(rule.param("threshold").defaultValue()).isEqualTo("15");
    }
}
