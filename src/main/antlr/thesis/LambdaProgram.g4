grammar LambdaProgram;

@header {
    package thesis;
}

program : (expression SEMICOLON)* EOF;

expression : typeDefinition
    | lambdaTypeDeclaration
    | lambdaDefinition ;

typeDefinition : TYPE_KEYWORD typeLiteral EQUALS typeExpression;

typeExpression : typeSumOperand (TYPE_PLUS typeSumOperand)*;

typeSumOperand : typeLiteral (typeSumOperand)*?
    | LBR inner=typeSumOperand RBR;

typeLiteral : name=TYPE_NAME;

lambdaDefinition : name=lambdaName patternExpression* EQUALS lambdaExpression;

lambdaExpression : lambdaApplicationOperand+;

lambdaApplicationOperand : terminal=lambdaLiteral
    | LEARN_KEYWORD
    | LAMBDA lambdaName+ DOT body=lambdaExpression
    | LBR expr=lambdaExpression COLON type=typeDeclaration RBR
    | LBR inner=lambdaExpression RBR;

lambdaTypeDeclaration : TYPE_KEYWORD name=lambdaName EQUALS typeDeclaration;

typeDeclaration : typeDeclarationOperand (ARROW typeDeclaration)*;

typeDeclarationOperand : typeLiteral | LBR typeDeclaration RBR;

patternExpression : lambdaName
    | name=typeLiteral
    | LBR constructor=typeLiteral patternExpression+ RBR;

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

fragment UPPER_CASE : [A-Z];
fragment LOWER_CASE : [a-z];
fragment DIGIT : [0-9];
fragment NAME_CHARACTERS : LOWER_CASE | UPPER_CASE | DIGIT | '_';