package thesis.preprocess.ast

import thesis.LambdaProgramParser

/**
 * Extensions for ANTLR-generated parser
 *
 * Transform generated AST tree to more convenient one
 */

fun LambdaProgramParser.ProgramContext.toAst() = LambdaProgram(
        expression().map { it.toAst() }
)

fun LambdaProgramParser.ExpressionContext.toAst(): LambdaProgramExpression {
    if (typeDefinition() != null) {
        return typeDefinition().toAst()
    }
    if (lambdaDefinition() != null) {
        return lambdaDefinition().toAst()
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.TypeDefinitionContext.toAst(): TypeDefinition {
    val name = typeLiteral().toAst()
    val expression = typeExpression().toAst()
    return TypeDefinition(
            name.name,
            expression
    )
}

fun LambdaProgramParser.TypeLiteralContext.toAst() = TypeLiteral(text)

fun LambdaProgramParser.TypeExpressionContext.toAst(): TypeExpression {
    val operands = typeSumOperand().map { it.toAst() }
    return if (operands.size == 1) operands.first() else TypeSum(operands)
}

fun LambdaProgramParser.TypeSumOperandContext.toAst(): TypeExpression {
    if (inner != null) {
        return inner.toAst()
    }
    val name = typeLiteral().toAst().name
    val operands = typeSumOperand().map { it.toAst() }
    return if (operands.isEmpty()) TypeLiteral(name) else TypeProduct(name, operands)
}

fun LambdaProgramParser.LambdaDefinitionContext.toAst(): LambdaDefinition {
    val expression = lambdaExpression().toAst()
    return LambdaDefinition(
            name.text,
            expression
    )
}

fun LambdaProgramParser.LambdaExpressionContext.toAst(): LambdaExpression {
    if (terminal != null) {
        return LambdaLiteral(terminal.text)
    }
    if (lambdaName() != null && body != null) {
        return LambdaAbstraction(
                lambdaName().map { it.text },
                body.toAst()
        )
    }
    if (LBR() != null && lambdaExpression() != null && RBR() != null) {
        return LambdaApplication(
                lambdaExpression().first().toAst(),
                lambdaExpression().drop(1).map { it.toAst() }
        )
    }
    throw IllegalStateException("Unexpected AST node")
}
