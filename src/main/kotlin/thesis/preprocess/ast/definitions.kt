/**
 * AST for Lambda Program
 */
package thesis.preprocess.ast

import thesis.preprocess.expressions.*

data class LambdaProgram(val expressions: List<LambdaProgramExpression>)

sealed class LambdaProgramExpression

data class LambdaDefinition(
        val name: LambdaName,
        val patterns: List<Pattern>,
        val expression: Lambda
) : LambdaProgramExpression()

data class TypeDefinition(
        val name: TypeName,
        val expression: AlgebraicType
) : LambdaProgramExpression()

data class LambdaTypeDeclaration(
        val name: LambdaName,
        val type: Type
) : LambdaProgramExpression()