/**
 * Extensions for ANTLR-generated parser
 *
 * Transform generated AST tree to more convenient one
 */
package thesis.preprocess.ast

import thesis.LambdaProgramParser
import thesis.preprocess.expressions.algebraic.type.RawAlgebraicType
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.lambda.untyped.UntypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.raw.RawType

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

fun LambdaProgramParser.TypeLiteralContext.toAst() = RawAlgebraicType.SumOperand.Literal(text)

fun LambdaProgramParser.TypeExpressionContext.toAst(): RawAlgebraicType {
    val operands = typeSumOperand().map { it.toAst() }
    return RawAlgebraicType(operands)
}

fun LambdaProgramParser.TypeSumOperandContext.toAst(): RawAlgebraicType.SumOperand {
    val name = typeLiteral().first().toAst().name
    val operands = typeLiteral().drop(1).map { it.toAst() }
    return if (operands.isEmpty()) RawAlgebraicType.SumOperand.Literal(name)
    else RawAlgebraicType.SumOperand.Product(name, operands)
}

fun LambdaProgramParser.LambdaDefinitionContext.toAst(): LambdaDefinition {
    val expression = lambdaExpression().toAst()
    val patterns = patternExpression().map { it.toAst() }
    return LambdaDefinition(
            name.text,
            patterns,
            expression
    )
}

fun LambdaProgramParser.LambdaExpressionContext.toAst(): UntypedLambda {
    val operands = lambdaApplicationOperand().map { it.toAst() }
    if (operands.size == 1) {
        return operands.first()
    }
    return UntypedLambda.Application(
            operands.first(),
            operands.drop(1)
    )
}

fun LambdaProgramParser.LambdaApplicationOperandContext.toAst(): UntypedLambda {
    if (terminal != null) {
        return UntypedLambda.Literal(terminal.text)
    }
    if (LEARN_KEYWORD() != null) {
        return UntypedLambda.Trainable()
    }
    if (LET_KEYWORD() != null && letBindings() != null && body != null) {
        val body = body.toAst()
        val bindings = letBindings().toAst()
        return UntypedLambda.LetAbstraction(
                bindings,
                body
        )
    }
    if (expr != null && type != null) {
        return UntypedLambda.TypedExpression(
                expr.toAst(),
                type.toAst()
        )
    }
    if (inner != null) {
        return inner.toAst()
    }
    if (lambdaName() != null && body != null) {
        return UntypedLambda.Abstraction(
                lambdaName().map { UntypedLambda.Literal(it.text) },
                body.toAst()
        )
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.LetBindingsContext.toAst() = letBinding().map { it.toAst() }

fun LambdaProgramParser.LetBindingContext.toAst(): UntypedLambda.LetAbstraction.Binding {
    val name = lambdaName().text
    val expression = lambdaExpression().toAst()
    return UntypedLambda.LetAbstraction.Binding(name, expression)
}

fun LambdaProgramParser.ParametrisedTypeDeclarationContext.toAst(): Parametrised<RawType> {
    val type = typeDeclaration().toAst()
    val parameters = FORALL_KEYWORD()?.let { typeVariable().map { it.text } } ?: emptyList()
    return type.bindVariables(parameters)
}

fun LambdaProgramParser.TypeDeclarationContext.toAst(): RawType {
    val operands = typeDeclarationOperand().map { it.toAst() }
    return operands.reduceRight({ t, res -> RawType.Function(t, res) })
}

fun LambdaProgramParser.TypeDeclarationOperandContext.toAst(): RawType {
    if (typeLiteral() != null) {
        return RawType.Literal(typeLiteral().text)
    }
    if (typeVariable() != null) {
        return RawType.Variable(typeVariable().text)
    }
    if (typeDeclaration() != null) {
        return typeDeclaration().toAst()
    }
    throw IllegalStateException("Unexpected AST node")
}

fun LambdaProgramParser.LambdaTypeDeclarationContext.toAst(): LambdaTypeDeclaration {
    return LambdaTypeDeclaration(
            lambdaName().text,
            parametrisedTypeDeclaration().toAst()
    )
}

fun LambdaProgramParser.PatternExpressionContext.toAst(): UntypedPattern {
    if (lambdaName() != null) {
        return UntypedPattern.Variable(lambdaName().text)
    }
    if (name != null) {
        return UntypedPattern.Object(name.text)
    }
    if (constructor != null) {
        return UntypedPattern.Constructor(
                constructor.text,
                patternExpression().map { it.toAst() }
        )
    }
    throw IllegalStateException("Unexpected AST node")
}