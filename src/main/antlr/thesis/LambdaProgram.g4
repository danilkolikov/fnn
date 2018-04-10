grammar LambdaProgram;

@header {
    package thesis;
}

program : (expression SEMICOLON)* EOF;

expression : typeDefinition
    | lambdaTypeDeclaration
    | lambdaDefinition;

typeDefinition : TYPE_KEYWORD typeLiteral EQUALS typeExpression;

typeExpression : typeSumOperand (TYPE_PLUS typeSumOperand)*;

typeSumOperand : typeLiteral+;

typeLiteral : name=TYPE_NAME;

lambdaDefinition : name=lambdaName patternExpression* EQUALS lambdaExpression;

lambdaExpression : lambdaApplicationOperand+;

lambdaApplicationOperand : terminal=lambdaLiteral
    | LEARN_KEYWORD
    | LAMBDA lambdaName+ DOT body=lambdaExpression
    | LET_KEYWORD letBindings IN_KEYWORD body=lambdaExpression
    | LBR expr=lambdaExpression COLON type=parametrisedTypeDeclaration RBR
    | LBR inner=lambdaExpression RBR;

letBindings : letBinding (COMMA letBinding)*;
letBinding : lambdaName EQUALS lambdaExpression;

lambdaTypeDeclaration : TYPE_KEYWORD name=lambdaName EQUALS parametrisedTypeDeclaration;

parametrisedTypeDeclaration : typeDeclaration | FORALL_KEYWORD typeVariable+ DOT typeDeclaration;

typeDeclaration : typeDeclarationOperand (ARROW typeDeclarationOperand)*;

typeDeclarationOperand : typeLiteral | typeVariable | LBR typeDeclaration RBR;

patternExpression : lambdaName
    | name=typeLiteral
    | LBR constructor=typeLiteral patternExpression+ RBR;

typeVariable : EXPR_NAME;
lambdaName : EXPR_NAME;
lambdaLiteral : EXPR_NAME | TYPE_NAME;

// Whitespace
WS : [\t \r\n]+ -> skip;

// Identifiers
TYPE_NAME : UPPER_CASE NAME_CHARACTERS*;
EXPR_NAME : LOWER_CASE NAME_CHARACTERS*;

// Keywords
TYPE_KEYWORD : '@type';
LEARN_KEYWORD : '@learn';
LET_KEYWORD: '@let';
IN_KEYWORD: '@in';
FORALL_KEYWORD: '@forall';

// Special characters
EQUALS : '=';
LBR : '(';
RBR : ')';
COMMA : ',';
TYPE_PLUS : '|';
LAMBDA : '\\';
DOT : '.';
COLON : ':';
ARROW : '->';
SEMICOLON : ';';

// Comments
COMMENT : '--' ~('\r' | '\n')* -> skip;

fragment UPPER_CASE : [A-Z];
fragment LOWER_CASE : [a-z];
fragment DIGIT : [0-9];
fragment NAME_CHARACTERS : LOWER_CASE | UPPER_CASE | DIGIT | '_';