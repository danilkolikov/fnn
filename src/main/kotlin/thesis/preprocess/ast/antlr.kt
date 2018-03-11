/**
 * Extensions for ANTLR-generated parser
 *
 * Transform generated AST tree to more convenient one
 */
package thesis.preprocess.ast

import thesis.LambdaProgramParser
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.Type

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

fun LambdaProgramParser.TypeLiteralContext.toAst() = AlgebraicType.Literal(text)

fun LambdaProgramParser.TypeExpressionContext.toAst(): AlgebraicType {
    val operands = typeSumOperand().map { it.toAst() }
    return if (operands.size == 1) operands.first() else AlgebraicType.Sum(operands)
}

fun LambdaProgramParser.TypeSumOperandContext.toAst(): AlgebraicType {
    if (inner != null) {
        return inner.toAst()
    }
    val name = typeLiteral().toAst().name
    val operands = typeSumOperand().map { it.toAst() }
    return if (operands.isEmpty()) AlgebraicType.Literal(name) else AlgebraicType.Product(name, operands)
}

fun LambdaProgramParser.LambdaDefinitionContext.toAst(): LambdaDefinition {
    val expression = lambdaExpression().toAst()
    return LambdaDefinition(
            name.text,
            expression
    )
}

fun LambdaProgramParser.LambdaExpressionContext.toAst(): Lambda {
    val operands = lambdaApplicationOperand().map { it.toAst() }
    if (operands.size == 1) {
        return operands.first()
    }
    return Lambda.Application(
            operands.first(),
            operands.drop(1)
    )
}

fun LambdaProgramParser.LambdaApplicationOperandContext.toAst(): Lambda {
    if (terminal != null) {
        return Lambda.Literal(terminal.text)
    }
    if (expr != null && type != null) {
        return Lambda.TypedExpression(
                expr.toAst(),
                type.toAst()
        )
    }
    if (inner != null) {
        return inner.toAst()
    }
    if (lambdaName() != null && body != null) {
        return Lambda.Abstraction(
                lambdaName().map { it.text },
                body.toAst()
        )
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.TypeDeclarationContext.toAst(): Type {
    val first = typeDeclarationOperand().toAst()
    val rest = typeDeclaration().map { it.toAst() }
    if (rest.isEmpty()) {
        return first
    }
    return (listOf(first) + rest).reduceRight({ t, res -> Type.Function(t, res) })
}

fun LambdaProgramParser.TypeDeclarationOperandContext.toAst(): Type {
    if (typeLiteral() != null) {
        return Type.Literal(typeLiteral().text)
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