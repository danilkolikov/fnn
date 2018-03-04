grammar LambdaProgram;

@header {
    package thesis;
}

program : expression* EOF;

expression : typeDefinition | lambdaDefinition ;

typeDefinition : TYPE_KEYWORD typeLiteral EQUALS typeExpression;

typeExpression : typeSumOperand (TYPE_PLUS typeSumOperand)*;

typeSumOperand : typeLiteral (typeSumOperand)*?
    | LBR inner=typeSumOperand RBR;

typeLiteral : name=TYPE_NAME;

lambdaDefinition : name=lambdaName EQUALS lambdaExpression;

lambdaExpression : terminal=lambdaLiteral
    | LAMBDA lambdaName+ DOT body=lambdaExpression
    | LBR lambdaExpression+ RBR;

lambdaName : EXPR_NAME;
lambdaLiteral : EXPR_NAME | TYPE_NAME;

// Whitespace
WS : [\t \r\n]+ -> skip;

// Identifiers
TYPE_NAME : UPPER_CASE NAME_CHARACTERS*;
EXPR_NAME : LOWER_CASE NAME_CHARACTERS*;

// Keywords
TYPE_KEYWORD : '@type';
EQUALS : '=';
LBR : '(';
RBR : ')';
COMMA : ',';
TYPE_PLUS : '|';
LAMBDA : '\\';
DOT : '.';

fragment UPPER_CASE : [A-Z];
fragment LOWER_CASE : [a-z];
fragment DIGIT : [0-9];
fragment NAME_CHARACTERS : LOWER_CASE | UPPER_CASE | DIGIT | '_';