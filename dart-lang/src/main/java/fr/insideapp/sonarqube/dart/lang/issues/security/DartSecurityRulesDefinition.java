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
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

public class DartSecurityRulesDefinition implements RulesDefinition {

    public static final String REPOSITORY_KEY = "dartsecurity";
    public static final String REPOSITORY_NAME = "Dart Security";

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(REPOSITORY_KEY, Dart.KEY).setName(REPOSITORY_NAME);

        hotspot(repository, DartSecurityScanner.HARDCODED_CREDENTIALS, "Hardcoded credentials should not be used",
                "cwe:798", "owaspTop10-2021:a7", "owaspMobile-2024:M1");
        hotspot(repository, DartSecurityScanner.CLEARTEXT_HTTP, "Using clear-text protocols is security-sensitive",
                "cwe:319", "owaspTop10-2021:a2", "owaspMobile-2024:M5");
        hotspot(repository, DartSecurityScanner.WEAK_HASH, "Weak hashing algorithms should not be used",
                "cwe:327", "cwe:328", "owaspTop10-2021:a2", "owaspMobile-2024:M10");
        hotspot(repository, DartSecurityScanner.INSECURE_RANDOM, "Using a non-cryptographic random generator is security-sensitive",
                "cwe:330", "cwe:338", "owaspTop10-2021:a2", "owaspMobile-2024:M10");
        hotspot(repository, DartSecurityScanner.BAD_CERTIFICATE_TRUST, "Disabling TLS certificate validation is security-sensitive",
                "cwe:295", "owaspTop10-2021:a7", "owaspMobile-2024:M5");
        hotspot(repository, DartSecurityScanner.INSECURE_STORAGE, "Storing sensitive data in SharedPreferences is security-sensitive",
                "cwe:312", "owaspTop10-2021:a4", "owaspMobile-2024:M9");
        hotspot(repository, DartSecurityScanner.HARDCODED_ENCRYPTION_KEY, "Using a hardcoded cryptographic key is security-sensitive",
                "cwe:321", "owaspTop10-2021:a2", "owaspMobile-2024:M10");
        hotspot(repository, DartSecurityScanner.WEBVIEW_JAVASCRIPT, "Enabling JavaScript in a WebView is security-sensitive",
                "cwe:79", "owaspTop10-2021:a3", "owaspMobile-2024:M8");
        hotspot(repository, DartSecurityScanner.SENSITIVE_DATA_IN_LOG, "Logging sensitive data is security-sensitive",
                "cwe:532", "owaspTop10-2021:a9", "owaspMobile-2024:M9");

        vulnerability(repository, DartSecurityScanner.SQL_INJECTION, "SQL queries should not be vulnerable to injection attacks",
                "cwe:89", "owaspTop10-2021:a3", "owaspMobile-2024:M4");
        vulnerability(repository, DartSecurityScanner.COMMAND_INJECTION, "OS commands should not be vulnerable to injection attacks",
                "cwe:78", "owaspTop10-2021:a3", "owaspMobile-2024:M4");

        repository.done();
    }

    private void hotspot(NewRepository repository, String key, String name, String... securityStandards) {
        applyStandards(repository.createRule(key)
                .setName(name)
                .setType(RuleType.SECURITY_HOTSPOT)
                .setActivatedByDefault(true)
                .setHtmlDescription(getClass().getResource("/dartsecurity/" + key + ".html")), securityStandards);
    }

    private void vulnerability(NewRepository repository, String key, String name, String... securityStandards) {
        applyStandards(repository.createRule(key)
                .setName(name)
                .setType(RuleType.VULNERABILITY)
                .setActivatedByDefault(true)
                .setHtmlDescription(getClass().getResource("/dartsecurity/" + key + ".html")), securityStandards);
    }

    private void applyStandards(NewRule rule, String... securityStandards) {

        for (String standard : securityStandards) {
            final String value = standard.substring(standard.indexOf(':') + 1);
            if (standard.startsWith("cwe:")) {
                rule.addCwe(Integer.parseInt(value));
            } else if (standard.startsWith("owaspMobile-2024:")) {
                rule.addOwaspMobileTop10(RulesDefinition.OwaspMobileTop10Version.Y2024,
                        RulesDefinition.OwaspMobileTop10.valueOf(value));
            } else {
                rule.addOwaspTop10(RulesDefinition.OwaspTop10Version.Y2021,
                        RulesDefinition.OwaspTop10.valueOf(value.toUpperCase()));
            }
        }
    }
}
