/**
 * Extensions for ANTLR-generated parser
 *
 * Transform generated AST tree to more convenient one
 */
package thesis.preprocess.ast

import thesis.LambdaProgramParser
import thesis.preprocess.types.SimpleType
import thesis.preprocess.types.TypeFunction
import thesis.preprocess.types.TypeTerminal

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
    if (lambdaTypeDeclaration() != null) {
        return lambdaTypeDeclaration().toAst()
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
    val operands = lambdaApplicationOperand().map { it.toAst() }
    if (operands.size == 1) {
        return operands.first()
    }
    return LambdaApplication(
            operands.first(),
            operands.drop(1)
    )
}

fun LambdaProgramParser.LambdaApplicationOperandContext.toAst(): LambdaExpression {
    if (terminal != null) {
        return LambdaLiteral(terminal.text)
    }
    if (expr != null && type != null) {
        return LambdaTypedExpression(
                expr.toAst(),
                type.toAst()
        )
    }
    if (inner != null) {
        return inner.toAst()
    }
    if (lambdaName() != null && body != null) {
        return LambdaAbstraction(
                lambdaName().map { it.text },
                body.toAst()
        )
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.TypeDeclarationContext.toAst(): SimpleType {
    val first = typeDeclarationOperand().toAst()
    val rest = typeDeclaration().map { it.toAst() }
    if (rest.isEmpty()) {
        return first
    }
    return (listOf(first) + rest).reduceRight({ t, res -> TypeFunction(t, res) })
}

fun LambdaProgramParser.TypeDeclarationOperandContext.toAst(): SimpleType {
    if (typeLiteral() != null) {
        return TypeTerminal(typeLiteral().text)
    }
    if (typeDeclaration() != null) {
        return typeDeclaration().toAst()
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.LambdaTypeDeclarationContext.toAst(): LambdaTypeDeclaration {
    return LambdaTypeDeclaration(
            lambdaName().text,
            typeDeclaration().toAst()
    )
}