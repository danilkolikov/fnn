/**
 * AST for Lambda Program
 */
package thesis.preprocess.ast

import thesis.preprocess.expressions.*

data class LambdaProgram(val expressions: List<LambdaProgramExpression>)

sealed class LambdaProgramExpression

sealed class Definition<out E : Expression> : LambdaProgramExpression() {
    abstract val name: String
    abstract val expression: E
}

data class LambdaDefinition(
        override val name: LambdaName,
        override val expression: Lambda
) : Definition<Lambda>()

data class TypeDefinition(
        override val name: TypeName,
        override val expression: AlgebraicType
) : Definition<AlgebraicType>()

data class LambdaTypeDeclaration(
        val name: LambdaName,
        val type: Type
) : LambdaProgramExpression()