/**
 * AST for Lambda Program
 */
package thesis.preprocess.ast

import thesis.preprocess.types.SimpleType


typealias TypeName = String

typealias LambdaName = String

data class LambdaProgram(val expressions: List<LambdaProgramExpression>)

sealed class LambdaProgramExpression

sealed class Expression

sealed class Definition<out E : Expression> : LambdaProgramExpression() {
    abstract val name: String
    abstract val expression: E
}

data class LambdaDefinition(
        override val name: LambdaName,
        override val expression: LambdaExpression
) : Definition<LambdaExpression>()

data class TypeDefinition(
        override val name: TypeName,
        override val expression: TypeExpression
) : Definition<TypeExpression>()

sealed class TypeExpression : Expression()

data class TypeLiteral(val name: TypeName) : TypeExpression()

data class TypeSum(
        val operands: List<TypeExpression>
) : TypeExpression()

data class TypeProduct(
        val name: String,
        val operands: List<TypeExpression>
) : TypeExpression()

sealed class LambdaExpression : Expression()

data class LambdaLiteral(val name: LambdaName) : LambdaExpression()

data class LambdaTypedExpression(
        val expression: LambdaExpression,
        val type: SimpleType
) : LambdaExpression()

data class LambdaAbstraction(
        val arguments: List<LambdaName>,
        val expression: LambdaExpression
) : LambdaExpression()

data class LambdaApplication(
        val function: LambdaExpression,
        val arguments: List<LambdaExpression>
) : LambdaExpression()

data class LambdaTypeDeclaration(
        val name: LambdaName,
        val type: SimpleType
) : LambdaProgramExpression()
