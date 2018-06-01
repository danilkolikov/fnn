grammar LambdaProgram;

@header {
    package thesis;
}

program
    : (expression SEMICOLON)* EOF
    ;

expression
    : typeDefinition
    | lambdaTypeDeclaration
    | lambdaDefinition
    ;

typeDefinition
    : TYPE_KEYWORD typeLiteral typeVariable* EQUALS typeExpression
    ;

typeExpression
    : typeSumOperand (TYPE_PLUS typeSumOperand)*
    ;

typeSumOperand
    : typeLiteral
    | typeProduct
    ;

typeProduct
    : typeLiteral typeProductOperand+
    ;

typeProductOperand
    : typeLiteral
    | typeVariable
    | LBR typeProduct RBR
    ;

typeLiteral
    : TYPE_NAME
    ;

typeVariable
    : EXPR_NAME
    ;

lambdaDefinition
    : name=lambdaName patternExpression* EQUALS lambdaExpression
    ;

lambdaExpression
    : lambdaApplicationOperand+
    ;

lambdaApplicationOperand
    : terminal=lambdaLiteral
    | LEARN_KEYWORD expressionOptions?
    | LAMBDA lambdaName+ DOT body=lambdaExpression
    | LET_KEYWORD letBindings IN_KEYWORD body=lambdaExpression
    | REC_KEYWORD name=lambdaName IN_KEYWORD body=lambdaExpression
    | LBR expr=lambdaExpression COLON type=parametrisedTypeDeclaration RBR
    | LBR inner=lambdaExpression RBR
    ;

letBindings
    : letBinding (COMMA letBinding)*
    ;
letBinding
    : lambdaName EQUALS lambdaExpression
    ;

lambdaTypeDeclaration
    : TYPE_KEYWORD name=lambdaName EQUALS parametrisedTypeDeclaration
    ;

parametrisedTypeDeclaration
    : typeDeclaration
    | FORALL_KEYWORD typeVariable+ DOT typeDeclaration
    ;

typeDeclaration
    : typeDeclarationOperand (ARROW typeDeclarationOperand)*
    ;

typeDeclarationOperand
    : typeLiteral
    | typeVariable
    | typeApplication
    | LBR typeDeclaration RBR
    ;

typeApplication
    : typeLiteral typeApplicationOperand*
    ;

typeApplicationOperand
    : typeLiteral
    | typeVariable
    | LBR typeDeclaration RBR
    ;

patternExpression
    : lambdaName
    | name=typeLiteral
    | LBR constructor=typeLiteral patternExpression+ RBR
    ;

lambdaName
    : EXPR_NAME
    ;

lambdaLiteral
    : EXPR_NAME
    | TYPE_NAME
    ;

expressionOptions
    : LCBR expressionOption (COMMA expressionOption)* RCBR
    ;

expressionOption
    : name=EXPR_NAME EQUALS value=OPTION_NUMBER
    ;

// Whitespace
WS : [\t \r\n]+ -> skip;


// Identifiers
TYPE_NAME : UPPER_CASE NAME_CHARACTERS*;
EXPR_NAME : LOWER_CASE NAME_CHARACTERS*;

// Options
OPTION_NUMBER : DIGIT+;

// Keywords
TYPE_KEYWORD : '@type';
LEARN_KEYWORD : '@learn';
LET_KEYWORD: '@let';
IN_KEYWORD: '@in';
FORALL_KEYWORD: '@forall';
REC_KEYWORD : '@rec';

// Special characters
EQUALS : '=';
LBR : '(';
RBR : ')';
LCBR : '{';
RCBR : '}';
COMMA : ',';
TYPE_PLUS : '|';
LAMBDA : '\\';
DOT : '.';
COLON : ':';
ARROW : '->';
SEMICOLON : ';';

// Comments
COMMENT : '--' ~('\r' | '\n')* -> skip;

fragment UPPER_CASE : ('A'..'Z');
fragment LOWER_CASE : ('a'..'z');
fragment DIGIT : ('0'..'9');
fragment NAME_CHARACTERS : LOWER_CASE | UPPER_CASE | DIGIT | '_';