/*
 [The "BSD licence"]
 Copyright (c) 2019 Wener
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

AB: 13-Apr-19; newExpression conflict , renamed to nayaExpression
AB: 13-Apr-19; Replaced `type` with `dtype` to fix golang code gen.

*/

grammar Dart2;

compilationUnit: libraryDefinition | partDeclaration;

WHITESPACE
//  : ('\t' | ' ' | NEWLINE)+   -> skip
  :  [ \t\r\n\u000C\uFEFF]+ -> skip
  ;

// 8 Variables
variableDeclaration
  : declaredIdentifier (',' identifier)*
  ;

declaredIdentifier
  : metadata finalConstVarOrType identifier
  ;
finalConstVarOrType
  : 'late'? 'final' dtype?
  | 'const' dtype?
  | 'late'? varOrType
  ;
varOrType
  : 'var'
  | dtype
  ;

initializedVariableDeclaration
  : declaredIdentifier ('=' expression)? (','initializedIdentifier)*
  ;
initializedIdentifier
  : identifier ('=' expression)?
  ;
initializedIdentifierList
  : initializedIdentifier (',' initializedIdentifier)*
  ;




// 9 Functions
functionSignature
  : metadata returnType? identifier formalParameterPart
  ;
formalParameterPart
  : typeParameters? formalParameterList
  ;
returnType
  : 'void'
  | dtype
  ;

functionBody
  : 'async'? '=>' expression ';'
  | ('async' | 'async*' | 'sync*')? block
  ;
block
  : '{' statements '}'
  ;

// 9.2 Formal Parameters
formalParameterList
  : '(' ')'
  | '(' normalFormalParameters (',' optionalFormalParameters)? ','? ')'
  | '(' optionalFormalParameters ')'
  ;
normalFormalParameters
  : normalFormalParameter (',' normalFormalParameter)*
  ;
optionalFormalParameters
  : optionalPositionalFormalParameters
  | namedFormalParameters
  ;
optionalPositionalFormalParameters
  : '[' defaultFormalParameter (',' defaultFormalParameter)* ','? ']'
  ;
namedFormalParameters
  : '{' defaultNamedParameter (',' defaultNamedParameter)* ','? '}'
  ;

// 9.2.1 Required Formals
normalFormalParameter
  : functionFormalParameter
  | fieldFormalParameter
  | superFormalParameter
  | simpleFormalParameter
  ;
superFormalParameter
  : metadata finalConstVarOrType? 'super' '.' identifier formalParameterPart?
  ;
functionFormalParameter
  : metadata 'covariant'? returnType? identifier formalParameterPart
  ;
simpleFormalParameter
  : metadata 'covariant'? finalConstVarOrType identifier
  | metadata 'covariant'? identifier
  | metadata dtype
  ;
fieldFormalParameter
  : metadata finalConstVarOrType? 'this' '.' identifier formalParameterPart?
  ;

// 9.2.2 Optional Formals
defaultFormalParameter
  : normalFormalParameter ('=' expression)?
  ;
defaultNamedParameter
  : metadata 'required'? normalFormalParameter ('=' expression)?
  | metadata 'required'? normalFormalParameter (':' expression)?
  ;

// 10 Classes (updated for Dart 3 class modifiers)
classModifier
  : 'sealed'
  | 'base'
  | 'final'
  | 'interface'
  | 'mixin'
  ;
classDefinition
  : metadata classModifier* 'abstract'? 'class' identifier typeParameters?
    superclass? mixins? interfaces?
    '{' (metadata classMemberDefinition)* '}'
  | metadata 'abstract'? classModifier* 'class' identifier typeParameters?
    superclass? mixins? interfaces?
    '{' (metadata classMemberDefinition)* '}'
  | metadata 'abstract'? 'class' mixinApplicationClass
;
mixins
  : 'with' typeList
  ;
classMemberDefinition
  : declaration ';'
  | methodSignature functionBody
  ;
methodSignature
  : constructorSignature initializers?
  | factoryConstructorSignature
  | 'static'? functionSignature
  | 'static'? getterSignature
  | 'static'? setterSignature
  | operatorSignature
  ;


declaration
  : redirectingFactoryConstructorSignature
  | constantConstructorSignature (redirection | initializers)?
  | constructorSignature (redirection | initializers)?
  | 'external' constantConstructorSignature
  | 'external' constructorSignature
  | ('external' 'static'?)? getterSignature
  | ('external' 'static'?)? setterSignature
  | 'external'? operatorSignature
  | ('external' 'static'?)? functionSignature
  | 'static' 'late'? ('final' | 'const') dtype? staticFinalDeclarationList
  | 'late'? 'final' dtype? initializedIdentifierList
  | ('static' | 'covariant')? 'late'? ('var' | dtype) initializedIdentifierList
  ;

staticFinalDeclarationList
  : staticFinalDeclaration (',' staticFinalDeclaration)*
  ;
staticFinalDeclaration
  : identifier '=' expression
  ;

// 10.1.1 Operators
operatorSignature
  : returnType? 'operator' operator formalParameterList
  ;
// '[]' and '[]=' are composed of single tokens at the parser level so that
// empty list literals like [] and const [] can be lexed properly.
operator
  : '~' | binaryOperator | '[' ']' '=' | '[' ']'
  ;

binaryOperator
  : multiplicativeOperator
  | additiveOperator
  | shiftOperator
  | relationalOperator
  | '=='
  | bitwiseOperator
  ;
// 10.2 Getters
getterSignature
  : returnType? 'get' identifier
  ;
// 10.2 Setters
setterSignature
  : returnType? 'set' identifier formalParameterList
  ;

// 10.6 Constructors
constructorSignature
  : identifier ('.' identifier)? formalParameterList
  ;
redirection
  : ':' 'this' ('.' identifier)? arguments
  ;

initializers
  : ':' initializerListEntry (',' initializerListEntry)*
  ;
initializerListEntry
  : 'super' arguments
  | 'super' '.' identifier arguments
  | fieldInitializer
  | assertion
  ;
fieldInitializer
  : ('this' '.')? identifier '=' conditionalExpression cascadeSection*
  ;

// 10.6.2 Factories
factoryConstructorSignature
  : 'factory' identifier ('.' identifier)? formalParameterList
  ;
redirectingFactoryConstructorSignature
  : 'const'? 'factory' identifier ('.' identifier)? formalParameterList '='
    dtype ('.' identifier)?
  ;
// 10.6.3 Constant Constructors
constantConstructorSignature: 'const' qualified formalParameterList;

// 10.9 Supperclasses
superclass: 'extends' dtype;

// 10.10 SUperinterfaces
interfaces: 'implements' typeList;

// 12.1 Mixin Application
mixinApplicationClass
  : identifier typeParameters? '=' mixinApplication ';';
mixinApplication
  : dtype mixins interfaces?
  ;

// 13 Enums
enumType
  : metadata 'enum' identifier typeParameters? mixins? interfaces?
    '{' enumEntry (',' enumEntry)* ','? (';' (metadata classMemberDefinition)*)? '}'
  ;

enumEntry
  : metadata identifier ('.' identifier)? argumentPart?
  ;

// 14 Generics
typeParameter
  : metadata identifier ('extends' dtype)?
  ;
typeParameters
  : '<' typeParameter (',' typeParameter)* '>'
  ;

// 15 Metadata
metadata
  : ('@' qualified ('.' identifier)? arguments?)*
  ;

// 16 Expressions
expression
  : assignableExpression assignmentOperator expression
  | conditionalExpression cascadeSection*
  | throwExpression
  ;
expressionWithoutCascade
  : assignableExpression assignmentOperator expressionWithoutCascade
  | conditionalExpression
  | throwExpressionWithoutCascade
  ;
expressionList
  : expression (',' expression)*
  ;
primary
  : thisExpression
  | 'super' unconditionalAssignableSelector
  | functionExpression
  | literal
  | constructorInvocation
  | identifier
  | nayaExpression
  | constObjectExpression
  | recordLiteral
  | switchExpression
  | '(' expression ')'
  ;

// Generic constructor invocation, e.g. List<int>.filled(3, 0)
constructorInvocation
  : typeName typeArguments '.' (identifier | 'new') arguments
  ;

// Dart 3 Records
recordLiteral
  : 'const'? '(' ')'
  | 'const'? '(' recordField ',' ')'
  | 'const'? '(' recordField (',' recordField)+ ','? ')'
  | 'const'? '(' identifier ':' expression ','? ')'
  ;
recordField
  : (identifier ':')? expression
  ;
recordType
  : '(' ')'
  | '(' recordTypeFields ','? ')'
  | '(' recordTypeFields ',' recordTypeNamedFields ')'
  | '(' recordTypeNamedFields ')'
  ;
recordTypeFields
  : recordTypeField (',' recordTypeField)*
  ;
recordTypeField
  : metadata dtype identifier?
  ;
recordTypeNamedFields
  : '{' recordTypeNamedField (',' recordTypeNamedField)* ','? '}'
  ;
recordTypeNamedField
  : metadata dtype identifier
  ;

// Dart 3 Switch Expressions
switchExpression
  : 'switch' '(' expression ')' '{' switchExpressionCase (',' switchExpressionCase)* ','? '}'
  ;
switchExpressionCase
  : guardedPattern '=>' expression
  ;
guardedPattern
  : pattern ('when' expression)?
  ;
pattern
  : logicalOrPattern
  ;
logicalOrPattern
  : logicalAndPattern ('||' logicalAndPattern)*
  ;
logicalAndPattern
  : unaryPattern ('&&' unaryPattern)*
  ;
unaryPattern
  : relationalPattern
  | primaryPattern ('?' | '!' | 'as' dtype)?
  ;
relationalPattern
  : (equalityOperator | relationalOperator) bitwiseOrExpression
  ;
primaryPattern
  : listPattern
  | mapPattern
  | recordPattern
  | variablePattern
  | objectPattern
  | typeTestPattern
  | wildcardPattern
  | constantPattern
  ;
constantPattern
  : literal
  | identifier
  | qualified
  | constObjectExpression
  ;
typeTestPattern
  : dtype identifier
  ;
wildcardPattern
  : '_'
  ;
variablePattern
  : ('var' | 'final' dtype? ) identifier
  ;
listPattern
  : typeArguments? '[' (listPatternElement (',' listPatternElement)* ','?)? ']'
  ;
listPatternElement
  : pattern
  | restPattern
  ;
restPattern
  : '...' pattern?
  ;
mapPattern
  : typeArguments? '{' (mapPatternEntry (',' mapPatternEntry)* ','?)? '}'
  ;
mapPatternEntry
  : expression ':' pattern
  | '...'
  ;
recordPattern
  : '(' (patternField (',' patternField)* ','?)? ')'
  ;
patternField
  : (identifier? ':')? pattern
  ;
objectPattern
  : typeName typeArguments? '(' (patternField (',' patternField)* ','?)? ')'
  ;
outerPattern
  : recordPattern
  | listPattern
  | mapPattern
  | objectPattern
  ;
patternVariableDeclaration
  : ('final' | 'var') outerPattern '=' expression
  ;

// 16.1 Constants

literal
  : nullLiteral
  | booleanLiteral
  | numericLiteral
  | stringLiteral
  | symbolLiteral
  | mapLiteral
  | listLiteral
  ;
nullLiteral: 'null';

numericLiteral
  : NUMBER
  | HEX_NUMBER
  ;

NUMBER
  : DIGIT+ ('.' DIGIT+)? EXPONENT?
  | '.' DIGIT+ EXPONENT?
  ;
fragment
EXPONENT
  : ('e' | 'E') ('+' | '-')? DIGIT+
  ;
HEX_NUMBER
  : '0x' HEX_DIGIT+
  | '0X' HEX_DIGIT+
  ;
fragment
HEX_DIGIT
  : [a-f]
  | [A-F]
  | DIGIT
  ;

booleanLiteral
  : 'true'
  | 'false'
  ;

stringLiteral: (MultiLineString | SingleLineString)+;

SingleLineString
  : '"' StringContentDQ* '"'
  | '\'' StringContentSQ* '\''
  | 'r\'' (~('\'' | '\n' | '\r'))* '\''
  | 'r"' (~('"' | '\n' | '\r'))* '"'
  ;

// Balanced ${...} interpolation blocks are consumed inside string tokens so
// that quotes nested in interpolations (e.g. '${DateFormat('dd')}') do not
// terminate the surrounding string.
// Newlines are allowed inside ${...} even in single-line strings.
fragment
EMBEDDED_EXPR
  : '${' (EMBEDDED_EXPR | ~[{}])* '}'
  ;

fragment
StringContentDQ
  : ~('\\' | '"' | '$' | '\n' | '\r')
  | '\\' ~('\n' | '\r')
  | EMBEDDED_EXPR
  | '$'
  ;

fragment
StringContentSQ
  : ~('\\' | '\'' | '$' | '\n' | '\r')
  | '\\' ~('\n' | '\r')
  | EMBEDDED_EXPR
  | '$'
  ;

MultiLineString
  : '"""' StringContentTDQ* '"""'
  | '\'\'\'' StringContentTSQ* '\'\'\''
  | 'r"""' (~'"' | '"' ~'"' | '""' ~'"')* '"""'
  | 'r\'\'\'' (~'\'' | '\'' ~'\'' | '\'\'' ~'\'')* '\'\'\''
  ;

fragment
StringContentTDQ
  : ~('\\' | '"' | '$')
  | '"' ~'"' | '""' ~'"'
  | EMBEDDED_EXPR
  | '$'
  ;

fragment StringContentTSQ
  : ~('\\' | '\'' | '$')
  | '\'' ~'\'' | '\'\'' ~'\''
  | EMBEDDED_EXPR
  | '$'
  ;

NEWLINE
  : '\n'
  | '\r'
  | '\r\n'
  ;

// 16.5.1 String Interpolation
stringInterpolation
//  : '$' IDENTIFIER_NO_DOLLAR
  : '$' identifier// FIXME
  | '${' expression '}'
  ;

// 16.6 Symbols
symbolLiteral
  : '#' (operator | (identifier (',' identifier)*))
  ;
// 16.7 Lists
listLiteral
  : 'const'? typeArguments? '[' elements? ']'
  ;

// 16.8 Maps and Sets
mapLiteral
  : 'const'? typeArguments? '{' elements? '}'
;
mapLiteralEntry
  : expression ':' expression
  ;

// Collection elements (spread, if, for - Dart 2.3+)
elements
  : element (',' element)* ','?
  ;
element
  : mapLiteralEntry
  | spreadElement
  | ifElement
  | forElement
  | expression
  ;
spreadElement
  : ('...' | '...?') expression
  ;
ifElement
  : 'if' '(' expression ('case' guardedPattern)? ')' element ('else' element)?
  ;
forElement
  : 'await'? 'for' '(' forLoopParts ')' element
  ;

// 16.9 Throw
throwExpression
  : 'throw' expression
  ;
throwExpressionWithoutCascade
  : 'throw' expressionWithoutCascade
  ;

// 16.10 Function Expressions
functionExpression
  : formalParameterPart functionExpressionBody
  ;
functionExpressionBody
  : 'async'? '=>' expression
  | ('async' | 'async*' | 'sync*')? block
  ;

// 16.11 This
thisExpression: 'this';

// 16.12.1 New
nayaExpression
  : 'new' dtype ('.' identifier)? arguments
  ;

// 16.12.2 Const
constObjectExpression
  : 'const' dtype ('.' identifier)? arguments
  ;

// 16.14.1 Actual Argument List Evaluation
arguments
  : '(' (argumentList ','?)? ')'
  ;
argumentList
  : namedArgument (',' namedArgument)*
  | expressionList (',' namedArgument)*
  ;
namedArgument
  : label expression
  ;

// 16.18.2 Cascaded Invocations
cascadeSection
  : ('..' | '?..') (cascadeSelector argumentPart*)
         (assignableSelector argumentPart*)*
         (assignmentOperator expressionWithoutCascade)?
  ;
cascadeSelector
  : '[' expression ']'
  | identifier
  ;
argumentPart
  : typeArguments? arguments
  ;

// 16.20 Assignment
assignmentOperator
  : '='
  | compoundAssignmentOperator
  ;

// 16.20.1 Compound Assignment
compoundAssignmentOperator
  : '*='
  | '/='
  | '~/='
  | '%='
  | '+='
  | '<<='
  | '>>='
  | '>>>='
  | '&='
  | '^='
  | '|='
  | '??='
  ;

// 16.21 Conditional
conditionalExpression
  : ifNullExpression
    ('?' expressionWithoutCascade ':' expressionWithoutCascade)?
  ;
// 16.22 If-null Expression
ifNullExpression
  : logicalOrExpression ('??' logicalOrExpression)*
  ;

// 16.23 Logical Boolean Expressions
logicalOrExpression
  : logicalAndExpression ('||' logicalAndExpression)*
  ;
logicalAndExpression
  : equalityExpression ('&&' equalityExpression)*
  ;

// 16.24 Equality
equalityExpression
  : relationalExpression (equalityOperator relationalExpression)?
  | 'super' equalityOperator relationalExpression
  ;
equalityOperator
  : '=='
  | '!='
  ;

// 16.25 Relational Expressions
relationalExpression
  : bitwiseOrExpression
    (
      typeTest
      | typeCast
      | relationalOperator bitwiseOrExpression
    )?
  | 'super' relationalOperator bitwiseOrExpression
  ;
relationalOperator
  : '>='
  | '>'
  | '<='
  | '<'
  ;

// 16.26 Bitwize Expression
bitwiseOrExpression
  : bitwiseXorExpression ('|' bitwiseXorExpression)*
  | 'super' ('|' bitwiseOrExpression)+
  ;
bitwiseXorExpression
  : bitwiseAndExpression ('^' bitwiseAndExpression)*
  | 'super' ('^' bitwiseAndExpression)+
  ;
bitwiseAndExpression
  : shiftExpression ('&' shiftExpression)*
  | 'super' ('&' shiftExpression)+
  ;
bitwiseOperator
  : '&'
  | '^'
  | '|'
  ;

// 16.27 Shift
shiftExpression
  : additiveExpression (shiftOperator additiveExpression)*
  | 'super' (shiftOperator additiveExpression)+
  ;
// '>>' and '>>>' are composed of single '>' tokens at the parser level so
// that nested type arguments like List<List<int>> can close properly.
shiftOperator
  : '<<'
  | '>' '>' '>'
  | '>' '>'
  ;

// 16.28 Additive Expression
additiveExpression
  : multiplicativeExpression (additiveOperator multiplicativeExpression)*
  | 'super' (additiveOperator multiplicativeExpression)+
  ;
additiveOperator
  : '+'
  | '-'
  ;
// 16.29 Multiplicative Expression
multiplicativeExpression
  : unaryExpression (multiplicativeOperator unaryExpression)*
  | 'super' (multiplicativeOperator unaryExpression)+
  ;
multiplicativeOperator
  : '*'
  | '/'
  | '%'
  | '~/'
  ;

// 16.30 Unary Expression
unaryExpression
  : prefixOperator unaryExpression
  | awaitExpression
  | postfixExpression
  | (minusOperator | tildeOperator) 'super'
  | incrementOperator assignableExpression
  ;
prefixOperator
  : minusOperator
  | negationOperator
  | tildeOperator
  ;
minusOperator: '-';
negationOperator: '!';
tildeOperator: '~';

// 16.31 Await Expressions
awaitExpression
  : 'await' unaryExpression
  ;

// 16.32 Postfix Expressions
postfixExpression
  : assignableExpression postfixOperator
  | primary selector*
  ;
postfixOperator
  : incrementOperator
  ;
selector
  : '!'
  | assignableSelector
  | argumentPart
  ;

incrementOperator
  : '++'
  | '--'
  ;
// 16.33 Assignable Expressions
assignableExpression
  : primary (argumentPart* assignableSelector)+
  | identifier
  | 'super' unconditionalAssignableSelector
  ;
unconditionalAssignableSelector
  : '[' expression ']'
  | '.' identifier
  | '.' 'new'
  ;
assignableSelector
  : unconditionalAssignableSelector
  | '?.' identifier
  | '?' '[' expression ']'
  ;

identifier
  : IDENTIFIER
  // Built-in identifiers and contextual keywords are still usable as plain
  // identifiers in Dart (e.g. http.get, Theme.of, mocktail's when).
  | 'abstract' | 'as' | 'covariant' | 'deferred' | 'export' | 'external'
  | 'factory' | 'Function' | 'get' | 'implements' | 'import' | 'interface'
  | 'late' | 'library' | 'mixin' | 'operator' | 'part' | 'required'
  | 'sealed' | 'set' | 'static' | 'typedef' | 'base' | 'when'
  | 'async' | 'hide' | 'of' | 'on' | 'show'
  | 'extension' | 'type'
  | '_'
  ;
qualified
  : identifier ('.' identifier)?
  ;
// 16.35 Type Test
typeTest
  : isOperator dtype
  ;
isOperator
  : 'is' '!'?
  ;

// 16.36 Type Cast
typeCast
  : asOperator dtype
  ;
asOperator
  : 'as'
  ;
// 17 Statements
statements
  : statement*
  ;
statement
  : label* nonLabledStatment
  ;
nonLabledStatment
  : block
  | localVariableDeclaration
  | forStatement
  | whileStatement
  | doStatement
  | switchStatement
  | ifStatement
  | rethrowStatment
  | tryStatement
  | breakStatement
  | continueStatement
  | returnStatement
  | yieldStatement
  | yieldEachStatement
  | expressionStatement
  | assertStatement
  | localFunctionDeclaration
  ;

// 17.2 Expression Statements
expressionStatement
  : expression? ';'
  ;

// 17.3 Local Variable Declaration
localVariableDeclaration
  : initializedVariableDeclaration ';'
  | patternVariableDeclaration ';'
  ;
// 17.4 Local Function Declaration
localFunctionDeclaration
  : functionSignature functionBody
  ;
// 17.5 If (updated for Dart 3 if-case)
ifStatement
  : 'if' '(' expression ')' statement ('else' statement)?
  | 'if' '(' expression 'case' pattern ('when' expression)? ')' statement ('else' statement)?
  ;

// 17.6 For for
forStatement
  : 'await'? 'for' '(' forLoopParts ')' statement
  ;
forLoopParts
  : forInitializerStatement expression? ';' expressionList?
  | declaredIdentifier 'in' expression
  | identifier 'in' expression
  | ('final' | 'var') outerPattern 'in' expression
  ;
forInitializerStatement
  : localVariableDeclaration
  | expression? ';'
  ;

// 17.7 While

whileStatement
  : 'while' '(' expression ')' statement
  ;
// 17.8 Do
doStatement
  : 'do' statement 'while' '(' expression ')' ';'
  ;
// 17.9 Switch (updated for Dart 3 patterns and when guards)
switchStatement
  : 'switch'  '(' expression ')' '{' switchCase* defaultCase? '}'
  ;
switchCase
  : label* 'case' guardedPattern ':' statements
  ;
defaultCase
  : label* 'default' ':' statements
  ;

// 17.10 Rethrow
rethrowStatment
  : 'rethrow' ';'
  ;

// 17.11 Try
tryStatement
  : 'try' block (onPart+ finallyPart? | finallyPart)
  ;
onPart
  : catchPart block
  | 'on' dtype catchPart? block
  ;
catchPart
  : 'catch' '(' identifier (',' identifier)? ')'
  ;
finallyPart
  : 'finally' block
  ;

// 17.12 Return

returnStatement
  : 'return' expression? ';'
  ;

// 17.13 Labels
label
  : identifier ':'
  ;

// 17.13 Break
breakStatement
  : 'break' identifier? ';'
  ;

// 17.13 Continue
continueStatement
  : 'continue' identifier? ';'
  ;

// 17.16.1 Yield
yieldStatement
  : 'yield' expression ';'
  ;
// 17.16.1 Yield-Each
yieldEachStatement
  : 'yield*' expression ';'
  ;

// 17.17 Assert
assertStatement
  : assertion ';'
  ;
assertion
  : 'assert' '(' expression (',' expression )? ','? ')'
  ;

// 18 Libraries and Scripts
topLevelDefinition
  : classDefinition
  | mixinDeclaration
  | extensionTypeDeclaration
  | extensionDeclaration
  | enumType
  | typeAlias
  | 'external'? functionSignature ';'
  | 'external'? getterSignature ';'
  | 'external'? setterSignature ';'
  | functionSignature functionBody
  | returnType? 'get' identifier functionBody
  | returnType? 'set' identifier formalParameterList functionBody
  | ('late'? 'final' | 'const') dtype? staticFinalDeclarationList ';'
  | initializedVariableDeclaration ';'
  | patternVariableDeclaration ';'
  ;

// 12 Mixin Declaration (Dart 2.1+)
mixinDeclaration
  : metadata 'base'? 'mixin' identifier typeParameters? ('on' typeList)? interfaces?
    '{' (metadata classMemberDefinition)* '}'
  ;

// Extension Declaration (Dart 2.7+)
extensionDeclaration
  : metadata 'extension' identifier? typeParameters? 'on' dtype
    '{' (metadata classMemberDefinition)* '}'
  ;

// Extension Types (Dart 3.3+)
extensionTypeDeclaration
  : metadata 'extension' 'type' 'const'? identifier typeParameters?
    '(' dtype identifier ')' interfaces?
    '{' (metadata classMemberDefinition)* '}'
  ;
getOrSet
  : 'get'
  | 'set'
  ;
libraryDefinition
  : scriptTag? libraryName? importOrExport* partDirective*
    topLevelDefinition*
  ;
scriptTag
  :  '#!' (~NEWLINE)* NEWLINE
  ;

libraryName
  : metadata 'library' dottedIdentifierList ';'
  ;
importOrExport
  : libraryimport
  | libraryExport
  ;
dottedIdentifierList
  : identifier (',' identifier)*
  ;

libraryimport
  : metadata importSpecification
  ;

importSpecification
  : 'import' configurableUri ('deferred'? 'as' identifier)? combinator* ';'
  ;

combinator
  : 'show' identifierList
  | 'hide' identifierList
  ;
identifierList
  : identifier (',' identifier)*
  ;

// 18.2 Exports
libraryExport
  : metadata 'export' configurableUri combinator* ';'
  ;

// 18.3 Parts
partDirective
  : metadata 'part' uri ';'
  ;
partHeader
  : metadata 'part' 'of' (identifier ('.' identifier)* | uri) ';'
  ;
partDeclaration
  : partHeader topLevelDefinition* EOF
  ;

// 18.5 URIs
uri
  : stringLiteral
  ;
configurableUri
  : uri configurationUri*
  ;
configurationUri
  : 'if' '(' uriTest ')' uri
  ;
uriTest
  : dottedIdentifierList ('==' stringLiteral)?
  ;

// 19.1 Static Types
dtype
  : (typeName typeArguments? | recordType) '?'? ('Function' typeParameters? formalParameterList '?'?)*
  | ('Function' typeParameters? formalParameterList '?'?)+
  ;
typeName
  : qualified
  | 'void' // SyntaxFix
  ;
typeArguments
  : '<' typeList '>'
  ;
typeList
  : dtype (',' dtype)*
  ;

// 19.3.1 Typedef
typeAlias
  : metadata 'typedef' typeAliasBody
  ;
typeAliasBody
  : functionTypeAlias
  | identifier typeParameters? '=' dtype ';'
  ;
functionTypeAlias
  : functionPrefix typeParameters? formalParameterList ';'
  ;
functionPrefix
  : returnType? identifier
  ;

// 20.2 Lexical Rules
// 20.1.1 Reserved Words
//assert, break, case, catch, class, const, continue, default, do, else,
//enum, extends, false, final, finally, for, if, in, is, new, null, rethrow,
//return, super, switch, this, throw, true, try, var, void, while, with.
fragment
IDENTIFIER_NO_DOLLAR
  : IDENTIFIER_START_NO_DOLLAR
    IDENTIFIER_PART_NO_DOLLAR*
  ;
IDENTIFIER
  : IDENTIFIER_START IDENTIFIER_PART*
  ;

//BUILT_IN_IDENTIFIER
//  : 'abstract'
//  | 'as'
//  | 'covariant'
//  | 'deferred'
//  | 'dynamic'
//  | 'export'
//  | 'external'
//  | 'factory'
//  | 'Function'
//  | 'get'
//  | 'implements'
//  | 'import'
//  | 'interface'
//  | 'library'
//  | 'operator'
//  | 'mixin'
//  | 'part'
//  | 'set'
//  | 'static'
//  | 'typedef'
//  ;
fragment
IDENTIFIER_START
  : IDENTIFIER_START_NO_DOLLAR
  | '$'
  ;
fragment
IDENTIFIER_START_NO_DOLLAR
  : LETTER
  | '_'
  ;
fragment
IDENTIFIER_PART_NO_DOLLAR
  : IDENTIFIER_START_NO_DOLLAR
  | DIGIT
  ;
fragment
IDENTIFIER_PART
  : IDENTIFIER_START
  | DIGIT
  ;

// 20.1.1 Reserved Words
fragment
LETTER
  : [a-z]
  | [A-Z]
  ;
fragment
DIGIT
  : [0-9]
  ;
// 20.1.2 Comments
SINGLE_LINE_COMMENT
//  : '//' ~(NEWLINE)* (NEWLINE)? // Origin Syntax
  : '//' ~[\r\n]* -> channel(2)
  ;
MULTI_LINE_COMMENT
//  : '/*' (MULTI_LINE_COMMENT | ~'*/')* '*/' // Origin Syntax
  : '/*' .*? '*/' -> channel(2)
  ;

