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

import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Lexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Token-based detector for security-sensitive constructs in Dart code.
 * Findings are reported as security hotspots: code that is not necessarily
 * vulnerable but deserves a security review.
 */
public class DartSecurityScanner {

    public static final String HARDCODED_CREDENTIALS = "hardcoded_credentials";
    public static final String CLEARTEXT_HTTP = "cleartext_http";
    public static final String WEAK_HASH = "weak_hash";
    public static final String INSECURE_RANDOM = "insecure_random";
    public static final String BAD_CERTIFICATE_TRUST = "bad_certificate_trust";
    public static final String INSECURE_STORAGE = "insecure_storage";
    public static final String HARDCODED_ENCRYPTION_KEY = "hardcoded_encryption_key";
    public static final String WEBVIEW_JAVASCRIPT = "webview_javascript";
    public static final String SENSITIVE_DATA_IN_LOG = "sensitive_data_in_log";

    // Vulnerabilities (typed VULNERABILITY, not hotspots)
    public static final String SQL_INJECTION = "sql_injection";
    public static final String COMMAND_INJECTION = "command_injection";

    /** Security hotspots (need human review). */
    public static final String[] RULE_KEYS = {
            HARDCODED_CREDENTIALS, CLEARTEXT_HTTP, WEAK_HASH, INSECURE_RANDOM, BAD_CERTIFICATE_TRUST,
            INSECURE_STORAGE, HARDCODED_ENCRYPTION_KEY, WEBVIEW_JAVASCRIPT, SENSITIVE_DATA_IN_LOG
    };

    /** Vulnerabilities (dynamic value reaching a dangerous sink). */
    public static final String[] VULNERABILITY_RULE_KEYS = {
            SQL_INJECTION, COMMAND_INJECTION
    };

    private static final java.util.Set<String> LOG_FUNCTIONS =
            new java.util.HashSet<>(java.util.Arrays.asList("print", "debugPrint", "log"));

    private static final java.util.Set<String> SQL_SINKS =
            new java.util.HashSet<>(java.util.Arrays.asList("rawQuery", "rawInsert", "rawUpdate", "rawDelete", "execute"));

    private static final java.util.Set<String> PROCESS_SINKS =
            new java.util.HashSet<>(java.util.Arrays.asList("run", "start", "runSync", "startSync"));

    // Simple ($id) or block (${...}) string interpolation.
    private static final Pattern STRING_INTERPOLATION = Pattern.compile("\\$[a-zA-Z_{]");

    private static final Pattern SHARED_PREFERENCES_SETTER =
            Pattern.compile("set(String|Int|Bool|Double|StringList)");

    private static final Pattern ENCRYPTION_KEY_FACTORY =
            Pattern.compile("from(Utf8|Base64|Base16|Length16)");

    private static final Pattern CREDENTIAL_IDENTIFIER =
            Pattern.compile("(?i).*(password|passwd|pwd|secret|api_?key|token|credential)s?$");

    private static final Pattern CLEARTEXT_URL =
            Pattern.compile("^(http|ws)://(?!localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|0\\.0\\.0\\.0).+");

    private static final int MIN_CREDENTIAL_LENGTH = 5;

    public List<SecurityFinding> scan(String source) {
        final Dart2Lexer lexer = new Dart2Lexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();

        final List<Token> tokens = new ArrayList<>();
        final java.util.Set<Integer> suppressedLines = new java.util.HashSet<>();
        for (Token token : stream.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                tokens.add(token);
            } else if (isSuppressionComment(token)) {
                suppressedLines.add(token.getLine());
            }
        }

        final List<SecurityFinding> raw = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (isString(token)) {
                checkCleartextUrl(token, raw);
            } else if (token.getType() == Dart2Lexer.IDENTIFIER) {
                checkCredentials(tokens, i, raw);
                checkWeakHash(token, raw);
                checkInsecureRandom(tokens, i, raw);
                checkBadCertificateTrust(token, raw);
                checkInsecureStorage(tokens, i, raw);
                checkHardcodedEncryptionKey(tokens, i, raw);
                checkWebviewJavascript(tokens, i, raw);
                checkSensitiveDataInLog(tokens, i, raw);
                checkInjection(tokens, i, raw);
            }
        }

        final List<SecurityFinding> findings = new ArrayList<>();
        for (SecurityFinding finding : raw) {
            if (!suppressedLines.contains(finding.getLine())) {
                findings.add(finding);
            }
        }
        return findings;
    }

    /**
     * A trailing '// NOSONAR' or '// ignore:' comment suppresses findings on the
     * same line, matching SonarQube's NOSONAR convention and Dart's own
     * '// ignore:' suppression.
     */
    private static boolean isSuppressionComment(Token token) {
        if (token.getType() != Dart2Lexer.SINGLE_LINE_COMMENT) {
            return false;
        }
        final String text = token.getText();
        return text.contains("NOSONAR") || text.matches("(?s).*//\\s*ignore(_for_file)?:.*");
    }

    private static boolean isString(Token token) {
        return token.getType() == Dart2Lexer.SingleLineString || token.getType() == Dart2Lexer.MultiLineString;
    }

    private static String stringContent(Token token) {
        final String text = token.getText();
        // Strip the surrounding quotes ("...", '...', """...""", '''...''', r'...')
        return text.replaceAll("^r?('''|\"\"\"|'|\")", "").replaceAll("('''|\"\"\"|'|\")$", "");
    }

    private void checkCleartextUrl(Token token, List<SecurityFinding> findings) {
        if (CLEARTEXT_URL.matcher(stringContent(token)).matches()) {
            findings.add(new SecurityFinding(CLEARTEXT_HTTP, token.getLine(),
                    "Using cleartext protocols is security-sensitive. Make sure this HTTP/WS connection does not carry sensitive data, or switch to HTTPS/WSS."));
        }
    }

    private void checkCredentials(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token identifier = tokens.get(index);
        if (!CREDENTIAL_IDENTIFIER.matcher(identifier.getText()).matches() || index + 2 >= tokens.size()) {
            return;
        }
        final String assignment = tokens.get(index + 1).getText();
        final Token value = tokens.get(index + 2);
        if (("=".equals(assignment) || ":".equals(assignment))
                && isString(value)
                && stringContent(value).length() >= MIN_CREDENTIAL_LENGTH) {
            findings.add(new SecurityFinding(HARDCODED_CREDENTIALS, value.getLine(),
                    String.format("'%s' looks like a hardcoded credential. Review it and prefer a secure configuration mechanism.", identifier.getText())));
        }
    }

    private void checkWeakHash(Token token, List<SecurityFinding> findings) {
        final String text = token.getText();
        if ("md5".equals(text) || "sha1".equals(text)) {
            findings.add(new SecurityFinding(WEAK_HASH, token.getLine(),
                    String.format("'%s' is a cryptographically weak hash algorithm. Make sure it is not used to protect sensitive data.", text)));
        }
    }

    private void checkInsecureRandom(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        if ("Random".equals(token.getText())
                && index + 1 < tokens.size()
                && "(".equals(tokens.get(index + 1).getText())) {
            findings.add(new SecurityFinding(INSECURE_RANDOM, token.getLine(),
                    "'Random()' is not cryptographically secure. Use 'Random.secure()' when randomness is used in a security context."));
        }
    }

    private void checkBadCertificateTrust(Token token, List<SecurityFinding> findings) {
        if ("badCertificateCallback".equals(token.getText())) {
            findings.add(new SecurityFinding(BAD_CERTIFICATE_TRUST, token.getLine(),
                    "Overriding 'badCertificateCallback' can disable TLS certificate validation. Make sure invalid certificates are not blindly trusted."));
        }
    }

    /**
     * Flags 'prefs.setString("token", ...)' style calls whose key looks
     * sensitive: SharedPreferences stores data in clear text, so credentials
     * and tokens should use a secure storage (Keychain / EncryptedSharedPreferences).
     */
    private void checkInsecureStorage(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        if (!SHARED_PREFERENCES_SETTER.matcher(token.getText()).matches()
                || index + 2 >= tokens.size()
                || !"(".equals(tokens.get(index + 1).getText())) {
            return;
        }
        final Token keyArgument = tokens.get(index + 2);
        if (isString(keyArgument)
                && CREDENTIAL_IDENTIFIER.matcher(stringContent(keyArgument)).matches()) {
            findings.add(new SecurityFinding(INSECURE_STORAGE, token.getLine(),
                    "Storing sensitive data in SharedPreferences is not secure. Use flutter_secure_storage (Keychain/EncryptedSharedPreferences) instead."));
        }
    }

    /**
     * Flags 'Key.fromUtf8("literal")' / 'IV.fromUtf8("literal")' and similar,
     * i.e. a cryptographic key or IV built from a hardcoded string.
     */
    private void checkHardcodedEncryptionKey(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        if (!ENCRYPTION_KEY_FACTORY.matcher(token.getText()).matches()
                || index < 2
                || index + 2 >= tokens.size()) {
            return;
        }
        final String receiver = tokens.get(index - 2).getText();
        final boolean dotBefore = ".".equals(tokens.get(index - 1).getText());
        final boolean openParen = "(".equals(tokens.get(index + 1).getText());
        if (dotBefore && openParen
                && ("Key".equals(receiver) || "IV".equals(receiver) || "SecretKey".equals(receiver))
                && isString(tokens.get(index + 2))) {
            findings.add(new SecurityFinding(HARDCODED_ENCRYPTION_KEY, token.getLine(),
                    "Using a hardcoded cryptographic key or IV is insecure. Derive it from a secret stored outside the source code."));
        }
    }

    /**
     * Flags enabling unrestricted JavaScript in a WebView, which broadens the
     * attack surface (script injection, bridge abuse).
     */
    private void checkWebviewJavascript(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        // 'JavaScriptMode.unrestricted' / 'JavascriptMode.unrestricted'
        if (("JavaScriptMode".equals(token.getText()) || "JavascriptMode".equals(token.getText()))
                && index + 2 < tokens.size()
                && ".".equals(tokens.get(index + 1).getText())
                && "unrestricted".equals(tokens.get(index + 2).getText())) {
            findings.add(new SecurityFinding(WEBVIEW_JAVASCRIPT, token.getLine(),
                    "Enabling unrestricted JavaScript in a WebView is security-sensitive. Make sure only trusted content is loaded."));
        }
    }

    /**
     * Flags passing a sensitive-named value straight to a logging function;
     * credentials must not end up in logs.
     */
    private void checkSensitiveDataInLog(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        if (!LOG_FUNCTIONS.contains(token.getText())
                || index + 2 >= tokens.size()
                || !"(".equals(tokens.get(index + 1).getText())) {
            return;
        }
        final Token argument = tokens.get(index + 2);
        if (argument.getType() == Dart2Lexer.IDENTIFIER
                && CREDENTIAL_IDENTIFIER.matcher(argument.getText()).matches()) {
            findings.add(new SecurityFinding(SENSITIVE_DATA_IN_LOG, token.getLine(),
                    "Logging sensitive data can leak it through log files. Do not log credentials or personal data."));
        }
    }

    /**
     * Flags a dynamically-built string (interpolation or concatenation) reaching
     * a dangerous sink: a raw SQL query or a process execution. This is a
     * pattern-based approximation of taint analysis - it does not follow data
     * flow across variables, but catches the classic injection anti-pattern.
     */
    private void checkInjection(List<Token> tokens, int index, List<SecurityFinding> findings) {
        final Token token = tokens.get(index);
        final boolean isSql = SQL_SINKS.contains(token.getText());
        final boolean isProcess = PROCESS_SINKS.contains(token.getText())
                && index >= 2
                && ".".equals(tokens.get(index - 1).getText())
                && "Process".equals(tokens.get(index - 2).getText());
        if ((!isSql && !isProcess) || index + 1 >= tokens.size()
                || !"(".equals(tokens.get(index + 1).getText())) {
            return;
        }
        if (argumentsAreDynamicString(tokens, index + 1)) {
            if (isSql) {
                findings.add(new SecurityFinding(SQL_INJECTION, token.getLine(),
                        "Building a raw SQL query from a dynamic string allows SQL injection. Use parameterized queries (placeholders and arguments)."));
            } else {
                findings.add(new SecurityFinding(COMMAND_INJECTION, token.getLine(),
                        "Building an OS command from a dynamic string allows command injection. Pass arguments as a list of constants and validate any external input."));
            }
        }
    }

    /**
     * Scans the argument list starting at the opening parenthesis and returns
     * true when it contains a string built dynamically: a string literal with
     * interpolation, or a '+' concatenation involving a string literal.
     */
    private boolean argumentsAreDynamicString(List<Token> tokens, int openParenIndex) {
        int depth = 0;
        boolean sawString = false;
        boolean sawPlus = false;
        for (int i = openParenIndex; i < tokens.size(); i++) {
            final Token t = tokens.get(i);
            final String text = t.getText();
            if ("(".equals(text)) {
                depth++;
            } else if (")".equals(text)) {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (isString(t)) {
                sawString = true;
                if (STRING_INTERPOLATION.matcher(stringContent(t)).find()) {
                    return true;
                }
            } else if ("+".equals(text)) {
                sawPlus = true;
            }
        }
        return sawString && sawPlus;
    }
}
