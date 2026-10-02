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
package fr.insideapp.sonarqube.dart.lang.antlr;

import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Lexer;
import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Parser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Dart3SyntaxParsingTest {

    private static List<String> syntaxErrors(String source) {
        final List<String> errors = new ArrayList<>();
        final BaseErrorListener listener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add("line " + line + ":" + charPositionInLine + " " + msg);
            }
        };
        final Dart2Lexer lexer = new Dart2Lexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        final Dart2Parser parser = new Dart2Parser(stream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        parser.compilationUnit();
        return errors;
    }

    private static void assertParses(String source) {
        assertThat(syntaxErrors(source)).isEmpty();
    }

    @Test
    public void nullableTypes() {
        assertParses("int? x;\n");
    }

    @Test
    public void lateFields() {
        assertParses("class A { late int x; }\n");
    }

    @Test
    public void superParameters() {
        assertParses("class B extends A { const B({super.key, required super.child}); }\n");
    }

    @Test
    public void enhancedEnums() {
        assertParses("enum E { a(1), b(2); final int v; const E(this.v); int get doubled => v * 2; }\n");
    }

    @Test
    public void constructorTearOff() {
        assertParses("final maker = DateTime.new;\n");
    }

    @Test
    public void recordTypeAnnotation() {
        assertParses("(int, String) pair = (1, 'a');\n");
    }

    @Test
    public void recordLiteralWithNamedFields() {
        assertParses("var r = (x: 1, y: 2);\n");
    }

    @Test
    public void recordDestructuring() {
        assertParses("void f() { final (a, b) = (1, 2); }\n");
    }

    @Test
    public void switchExpression() {
        assertParses("String f(int x) => switch (x) { 1 => 'one', _ => 'other' };\n");
    }

    @Test
    public void switchStatementWithWhenGuard() {
        assertParses("void f(Object o) { switch (o) { case int i when i > 0: break; default: break; } }\n");
    }

    @Test
    public void ifCasePattern() {
        assertParses("void f(Object o) { if (o case int i when i > 0) { print(i); } }\n");
    }

    @Test
    public void sealedClass() {
        assertParses("sealed class Shape {}\n");
    }

    @Test
    public void classModifiers() {
        assertParses("base class A {}\ninterface class B {}\nfinal class C {}\nmixin class D {}\n");
    }

    @Test
    public void collectionIfForAndSpread() {
        assertParses("List<int> f(bool x, List<int> rest, List<int>? maybe, List<int> items) =>\n" +
            "    [1, if (x) 2 else 3, ...rest, ...?maybe, for (final i in items) i * 2];\n");
    }

    @Test
    public void mapWithSpreadAndIf() {
        assertParses("Map<String, int> f(bool x, Map<String, int> other) => {'a': 1, ...other, if (x) 'b': 2};\n");
    }

    @Test
    public void setLiteral() {
        assertParses("final s = <int>{1, 2, 3};\n");
    }

    @Test
    public void extensionDeclaration() {
        assertParses("extension StringX on String { bool get blank => trim().isEmpty; }\n" +
            "extension on int { int get doubled => this * 2; }\n");
    }

    @Test
    public void mixinDeclaration() {
        assertParses("mixin Walker on Animal { void walk() {} }\nbase mixin M {}\n");
    }

    @Test
    public void functionTypes() {
        assertParses("class A { final void Function(int) callback; A(this.callback); }\n" +
            "void Function()? cb;\n" +
            "final Function untyped = print;\n");
    }

    @Test
    public void modernTypedefs() {
        assertParses("typedef IntList = List<int>;\ntypedef Cb = void Function(String);\n");
    }

    @Test
    public void nullAssertPostfix() {
        assertParses("void f(int? x) { print(x! + 1); }\n");
    }

    @Test
    public void nullAwareCascade() {
        assertParses("void f(StringBuffer? b) { b?..write('a')..write('b'); }\n");
    }

    @Test
    public void objectPatternsInSwitchExpression() {
        assertParses("String f(Object r) => switch (r) {\n" +
            "  Success(:final data) => data,\n" +
            "  Failure(error: final e) => e.toString(),\n" +
            "  _ => '',\n" +
            "};\n");
    }

    @Test
    public void listAndMapPatterns() {
        assertParses("void f(Object o) {\n" +
            "  if (o case [int a, _, ...]) { print(a); }\n" +
            "  if (o case {'k': var v}) { print(v); }\n" +
            "}\n");
    }

    @Test
    public void relationalAndLogicalPatterns() {
        assertParses("String g(int n) => switch (n) {\n" +
            "  < 0 => 'neg',\n" +
            "  0 || 1 => 'small',\n" +
            "  >= 100 && < 1000 => 'hundreds',\n" +
            "  _ => 'other',\n" +
            "};\n");
    }

    @Test
    public void forInWithPatternDestructuring() {
        assertParses("void f(List<(int, String)> l) { for (final (i, s) in l) { print(s); } }\n");
    }

    @Test
    public void contextualKeywordsAsIdentifiers() {
        assertParses("void f() { when(() => 1).thenReturn(2); }\n");
    }

    @Test
    public void freezedStyleRedirectingFactory() {
        assertParses("abstract class EnumOption with _$EnumOption {\n" +
            "  factory EnumOption({required String chave, required String descricao}) = _EnumOption;\n" +
            "  const factory EnumOption.empty() = _EnumOption2;\n" +
            "}\n");
    }

    @Test
    public void underscoreAsIdentifier() {
        assertParses("void f(List<int> items) { final _ = 1; items.map((_) => 2); }\n");
    }

    @Test
    public void trailingCommaInFormalParameters() {
        assertParses("void f(int a, int b,) {}\n" +
            "class C { const C(this.a, {this.b,}); final int a; final int? b; }\n");
    }

    @Test
    public void nestedGenerics() {
        assertParses("final List<List<int>> matrix = [[1]];\n" +
            "Future<Map<String, List<int>>> f() async => {};\n");
    }

    @Test
    public void shiftOperatorsStillWork() {
        assertParses("int f(int x) => (x << 2) >> 1 >>> 3;\n" +
            "void g(int x) { x >>= 1; x <<= 2; }\n");
    }

    @Test
    public void typicalGeneratedRetrofitClient() {
        assertParses("class _Api implements Api {\n" +
            "  _Api(this._dio, {this.baseUrl});\n" +
            "  final Dio _dio;\n" +
            "  String? baseUrl;\n" +
            "  @override\n" +
            "  Future<Resposta<List<Item>>> fetch() async {\n" +
            "    final options = _setStreamType<Resposta<List<Item>>>(Options(method: 'GET'));\n" +
            "    return Resposta<List<Item>>.fromJson(await _dio.fetch(options), (json) => Item.fromJson(json));\n" +
            "  }\n" +
            "}\n");
    }

    @Test
    public void emptyListLiterals() {
        assertParses("final a = [];\nconst b = <int>[];\n" +
            "class C { const C({List<int> data = const []}); }\n" +
            "@Default([]) class D {}\n");
    }

    @Test
    public void indexOperatorDeclarations() {
        assertParses("class C { int operator [](int i) => 0; void operator []=(int i, int v) {} }\n");
    }

    @Test
    public void chainedAssignableExpressions() {
        assertParses("void f(dynamic o) { o.headers['A'] = 'B'; o.a.b.c = 1; o.list[0].x += 2; }\n");
    }

    @Test
    public void stringInterpolationWithNestedQuotes() {
        assertParses("final s = 'x ${DateFormat('dd').format(now)} y';\n" +
            "final t = \"a ${map['key']} b\";\n" +
            "final u = '''m ${obj.call('arg')} n''';\n");
    }

    @Test
    public void utf8ByteOrderMark() {
        assertParses("﻿int x = 1;\n");
    }

    @Test
    public void annotationBeforeRequiredInNamedParameter() {
        assertParses("abstract class E { Future<int> f({@Query('a') required String anoLetivoId, @Query('b') bool? ativo}); }\n");
    }

    @Test
    public void extensionAndTypeAsIdentifiers() {
        assertParses("class F { final String extension; final ButtonType type; F(this.extension, this.type); }\n" +
            "void g(dynamic f) { print(f.extension); print(f.type); }\n");
    }

    @Test
    public void multilineInterpolationInSingleLineString() {
        assertParses("final s = 'Ola, ${controller.value == null\n" +
            "    ? ''\n" +
            "    : controller.value!.nome}';\n");
    }

    @Test
    public void covariantTypedParameter() {
        assertParses("class W { void didUpdateWidget(covariant W oldWidget) {} }\n");
    }

    @Test
    public void nullAwareIndexAndNullableCast() {
        assertParses("void f(dynamic e, dynamic json, bool cond) {\n" +
            "  final a = e.response?.data?['errors'] as String?;\n" +
            "  final b = json['j'] as Map<String, dynamic>?;\n" +
            "  final c = cond ? [1] : [2];\n" +
            "  final d = cond ? [1] : x?['k'];\n" +
            "}\n");
    }

    @Test
    public void typicalFlutterWidgetDart3() {
        assertParses(
            "class MyCard extends StatelessWidget {\n" +
            "  const MyCard({super.key, required this.status});\n" +
            "  final Status status;\n" +
            "  @override\n" +
            "  Widget build(BuildContext context) {\n" +
            "    final label = switch (status) {\n" +
            "      Status.open => 'Aberto',\n" +
            "      Status.closed => 'Fechado',\n" +
            "      _ => 'Outro',\n" +
            "    };\n" +
            "    return Text(label);\n" +
            "  }\n" +
            "}\n");
    }
}
