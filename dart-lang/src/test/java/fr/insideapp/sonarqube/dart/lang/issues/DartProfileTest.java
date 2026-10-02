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

import fr.insideapp.sonarqube.dart.lang.issues.security.DartSecurityRulesDefinition;
import fr.insideapp.sonarqube.dart.lang.issues.security.DartSecurityScanner;
import org.junit.Test;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class DartProfileTest {

    @Test
    public void profileActivatesSecurityHotspots() {
        DartProfile dartProfile = new DartProfile();
        BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
        dartProfile.define(context);

        BuiltInQualityProfilesDefinition.BuiltInQualityProfile profile =
                context.profile("dart", "dartanalyzer");
        assertThat(profile).isNotNull();

        assertThat(profile.rule(org.sonar.api.rule.RuleKey.of(
                DartSecurityRulesDefinition.REPOSITORY_KEY, DartSecurityScanner.HARDCODED_CREDENTIALS)))
                .as("hardcoded_credentials hotspot should be active in the default profile")
                .isNotNull();
        assertThat(profile.rule(org.sonar.api.rule.RuleKey.of(
                DartSecurityRulesDefinition.REPOSITORY_KEY, DartSecurityScanner.BAD_CERTIFICATE_TRUST)))
                .isNotNull();
    }
}
