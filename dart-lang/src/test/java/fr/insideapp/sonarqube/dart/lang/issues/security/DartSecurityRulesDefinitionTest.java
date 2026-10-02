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

import org.junit.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class DartSecurityRulesDefinitionTest {

    @Test
    public void define() {
        DartSecurityRulesDefinition rulesDefinition = new DartSecurityRulesDefinition();
        RulesDefinition.Context context = new RulesDefinition.Context();
        rulesDefinition.define(context);

        RulesDefinition.Repository repository = context.repository("dartsecurity");
        assertThat(repository).isNotNull();
        assertThat(repository.language()).isEqualTo("dart");
        assertThat(repository.rules()).hasSize(11);

        for (RulesDefinition.Rule rule : repository.rules()) {
            assertThat(rule.type()).as("rule %s", rule.key())
                    .isIn(RuleType.SECURITY_HOTSPOT, RuleType.VULNERABILITY);
            assertThat(rule.securityStandards()).as("rule %s should map CWEs", rule.key()).isNotEmpty();
            assertThat(rule.htmlDescription()).as("rule %s", rule.key()).isNotEmpty();
            assertThat(rule.activatedByDefault()).as("rule %s", rule.key()).isTrue();
        }

        // injection rules are typed as vulnerabilities, not hotspots
        assertThat(repository.rule(DartSecurityScanner.SQL_INJECTION).type()).isEqualTo(RuleType.VULNERABILITY);
        assertThat(repository.rule(DartSecurityScanner.COMMAND_INJECTION).type()).isEqualTo(RuleType.VULNERABILITY);
        assertThat(repository.rule(DartSecurityScanner.SQL_INJECTION).securityStandards()).contains("cwe:89");
        assertThat(repository.rule(DartSecurityScanner.COMMAND_INJECTION).securityStandards()).contains("cwe:78");

        assertThat(repository.rule(DartSecurityScanner.HARDCODED_CREDENTIALS).securityStandards())
                .contains("cwe:798");
        assertThat(repository.rule(DartSecurityScanner.CLEARTEXT_HTTP).securityStandards())
                .contains("cwe:319");
        assertThat(repository.rule(DartSecurityScanner.WEAK_HASH).securityStandards())
                .contains("cwe:327", "cwe:328");
        assertThat(repository.rule(DartSecurityScanner.INSECURE_RANDOM).securityStandards())
                .contains("cwe:330");
        assertThat(repository.rule(DartSecurityScanner.BAD_CERTIFICATE_TRUST).securityStandards())
                .contains("cwe:295");
        assertThat(repository.rule(DartSecurityScanner.INSECURE_STORAGE).securityStandards())
                .contains("cwe:312");
        assertThat(repository.rule(DartSecurityScanner.HARDCODED_ENCRYPTION_KEY).securityStandards())
                .contains("cwe:321");
        assertThat(repository.rule(DartSecurityScanner.WEBVIEW_JAVASCRIPT).securityStandards())
                .contains("cwe:79");
        assertThat(repository.rule(DartSecurityScanner.SENSITIVE_DATA_IN_LOG).securityStandards())
                .contains("cwe:532");

        // every rule maps to an OWASP Mobile Top 10 2024 category (the PDF's framework)
        for (RulesDefinition.Rule rule : repository.rules()) {
            boolean hasMobile = false;
            for (String standard : rule.securityStandards()) {
                if (standard.startsWith("owaspMobileTop10-2024:")) {
                    hasMobile = true;
                    break;
                }
            }
            assertThat(hasMobile).as("rule %s should map an OWASP Mobile category", rule.key()).isTrue();
        }
    }
}
