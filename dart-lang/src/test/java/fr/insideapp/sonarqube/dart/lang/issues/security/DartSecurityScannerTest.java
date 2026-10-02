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

public class DartSecurityScannerTest {

    private final DartSecurityScanner scanner = new DartSecurityScanner();

    private List<SecurityFinding> scan(String source) {
        return scanner.scan(source);
    }

    // hardcoded_credentials

    @Test
    public void hardcodedPasswordAssignment() {
        List<SecurityFinding> findings = scan("final password = 'hunter42secret';\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.HARDCODED_CREDENTIALS);
        assertThat(findings.get(0).getLine()).isEqualTo(1);
    }

    @Test
    public void hardcodedApiKeyNamedArgument() {
        List<SecurityFinding> findings = scan("void f() {\n  configure(apiKey: 'abcd1234efgh');\n}\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.HARDCODED_CREDENTIALS);
        assertThat(findings.get(0).getLine()).isEqualTo(2);
    }

    @Test
    public void emptyOrShortCredentialStringIsIgnored() {
        assertThat(scan("final apiKey = '';\nfinal pwd = 'x';\n")).isEmpty();
    }

    @Test
    public void credentialFromVariableIsIgnored() {
        assertThat(scan("final password = readFromEnv();\nApi(token: cachedToken);\n")).isEmpty();
    }

    // cleartext_http

    @Test
    public void cleartextHttpUrl() {
        List<SecurityFinding> findings = scan("final url = 'http://api.example.com/v1';\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.CLEARTEXT_HTTP);
    }

    @Test
    public void httpsAndLocalhostAreIgnored() {
        assertThat(scan("final a = 'https://api.example.com';\n" +
                "final b = 'http://localhost:8080';\n" +
                "final c = 'http://127.0.0.1/health';\n" +
                "final d = 'http://10.0.2.2:3000';\n")).isEmpty();
    }

    // weak_hash

    @Test
    public void md5AndSha1Usage() {
        List<SecurityFinding> findings = scan("final d = md5.convert(bytes);\nfinal e = sha1.convert(bytes);\n");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.WEAK_HASH);
        assertThat(findings.get(1).getLine()).isEqualTo(2);
    }

    @Test
    public void sha256IsFine() {
        assertThat(scan("final d = sha256.convert(bytes);\n")).isEmpty();
    }

    // insecure_random

    @Test
    public void insecureRandomConstructor() {
        List<SecurityFinding> findings = scan("final r = Random();\nfinal s = Random(42);\n");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.INSECURE_RANDOM);
    }

    @Test
    public void secureRandomIsFine() {
        assertThat(scan("final r = Random.secure();\n")).isEmpty();
    }

    // bad_certificate_trust

    @Test
    public void badCertificateCallback() {
        List<SecurityFinding> findings = scan(
            "void f(HttpClient c) { c.badCertificateCallback = (cert, host, port) => true; }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.BAD_CERTIFICATE_TRUST);
    }

    // insecure_storage (OWASP Mobile M9)

    @Test
    public void sensitiveDataStoredInSharedPreferences() {
        List<SecurityFinding> findings = scan(
            "Future<void> f(SharedPreferences prefs, String jwt) async {\n" +
            "  await prefs.setString('auth_token', jwt);\n" +
            "}\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.INSECURE_STORAGE);
        assertThat(findings.get(0).getLine()).isEqualTo(2);
    }

    @Test
    public void nonSensitiveSharedPreferencesKeyIsIgnored() {
        assertThat(scan("void f(SharedPreferences prefs) { prefs.setBool('dark_mode', true); }\n")).isEmpty();
    }

    // hardcoded_encryption_key (OWASP Mobile M10 - the Ola 'PRODKEYPRODKEY12' case)

    @Test
    public void hardcodedEncryptionKeyFromUtf8() {
        List<SecurityFinding> findings = scan("final key = Key.fromUtf8('PRODKEYPRODKEY12');\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.HARDCODED_ENCRYPTION_KEY);
    }

    @Test
    public void hardcodedIvIsFlagged() {
        List<SecurityFinding> findings = scan("final iv = IV.fromUtf8('0123456789abcdef');\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.HARDCODED_ENCRYPTION_KEY);
    }

    @Test
    public void encryptionKeyFromVariableIsIgnored() {
        assertThat(scan("final key = Key.fromUtf8(secretFromEnv);\n")).isEmpty();
    }

    // webview_javascript (OWASP Mobile M8)

    @Test
    public void webviewWithUnrestrictedJavascript() {
        List<SecurityFinding> findings = scan(
            "void f(WebViewController c) { c.setJavaScriptMode(JavaScriptMode.unrestricted); }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.WEBVIEW_JAVASCRIPT);
    }

    @Test
    public void webviewLegacyJavascriptEnabled() {
        List<SecurityFinding> findings = scan("final w = WebView(javascriptMode: JavascriptMode.unrestricted);\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.WEBVIEW_JAVASCRIPT);
    }

    @Test
    public void restrictedJavascriptModeIsIgnored() {
        assertThat(scan("final w = WebView(javascriptMode: JavascriptMode.disabled);\n")).isEmpty();
    }

    // sensitive_data_in_log (OWASP Mobile M9/privacy)

    @Test
    public void loggingSensitiveVariable() {
        List<SecurityFinding> findings = scan("void f(String password) { print(password); }\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.SENSITIVE_DATA_IN_LOG);
    }

    @Test
    public void loggingViaDebugPrintAndLog() {
        List<SecurityFinding> findings = scan(
            "void f(String token, String apiKey) { debugPrint(token); log(apiKey); }\n");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).getRuleKey()).isEqualTo(DartSecurityScanner.SENSITIVE_DATA_IN_LOG);
    }

    @Test
    public void loggingNonSensitiveValueIsIgnored() {
        assertThat(scan("void f(int count) { print(count); print('hello'); }\n")).isEmpty();
    }

    // comments are ignored

    @Test
    public void commentedOutCodeIsIgnored() {
        assertThat(scan("// final password = 'hunter42secret';\n" +
                "/* final url = 'http://api.example.com'; */\n")).isEmpty();
    }

    // suppression

    @Test
    public void nosonarCommentSuppressesFinding() {
        assertThat(scan("final password = 'hunter42secret'; // NOSONAR\n")).isEmpty();
    }

    @Test
    public void ignoreCommentSuppressesFinding() {
        assertThat(scan("final url = 'http://api.example.com'; // ignore: hardcoded\n")).isEmpty();
    }

    @Test
    public void suppressionOnlyAffectsItsOwnLine() {
        List<SecurityFinding> findings = scan(
            "final a = 'http://api.one.com'; // NOSONAR\n" +
            "final b = 'http://api.two.com';\n");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getLine()).isEqualTo(2);
    }
}
