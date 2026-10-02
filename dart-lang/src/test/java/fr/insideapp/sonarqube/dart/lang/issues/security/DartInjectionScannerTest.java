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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DartInjectionScannerTest {

    private final DartSecurityScanner scanner = new DartSecurityScanner();

    private List<SecurityFinding> scan(String source) {
        return scanner.scan(source);
    }

    // sql_injection (CWE-89)

    @Test
    public void rawQueryWithInterpolationIsSqlInjection() {
        List<SecurityFinding> findings = scan(
            "Future<void> f(Database db, String id) async {\n" +
            "  await db.rawQuery('SELECT * FROM users WHERE id = $id');\n" +
            "}\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.SQL_INJECTION);
        assertThat(findings.get(0).getLine()).isEqualTo(2);
    }

    @Test
    public void rawInsertWithConcatenationIsSqlInjection() {
        List<SecurityFinding> findings = scan(
            "void f(Database db, String name) { db.rawInsert('INSERT INTO t VALUES (' + name + ')'); }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.SQL_INJECTION);
    }

    @Test
    public void parameterizedQueryIsSafe() {
        assertThat(scan(
            "void f(Database db, String id) { db.rawQuery('SELECT * FROM users WHERE id = ?', [id]); }\n"))
            .isEmpty();
    }

    @Test
    public void constantQueryIsSafe() {
        assertThat(scan("void f(Database db) { db.rawQuery('SELECT * FROM users'); }\n")).isEmpty();
    }

    // command_injection (CWE-78)

    @Test
    public void processRunWithInterpolationIsCommandInjection() {
        List<SecurityFinding> findings = scan(
            "void f(String path) { Process.run('sh', ['-c', 'ls $path']); }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.COMMAND_INJECTION);
    }

    @Test
    public void processStartWithConcatenationIsCommandInjection() {
        List<SecurityFinding> findings = scan(
            "void f(String cmd) { Process.start('sh', ['-c', 'run ' + cmd]); }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.COMMAND_INJECTION);
    }

    @Test
    public void processRunWithConstantArgsIsSafe() {
        assertThat(scan("void f() { Process.run('ls', ['-la']); }\n")).isEmpty();
    }
}
