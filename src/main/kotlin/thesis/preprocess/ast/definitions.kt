/**
 * AST for Lambda Program
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.ast

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.RawAlgebraicType
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.lambda.untyped.UntypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.raw.RawType

data class LambdaProgram(val expressions: List<LambdaProgramExpression>)

sealed class LambdaProgramExpression

data class LambdaDefinition(
        val name: LambdaName,
        val patterns: List<UntypedPattern>,
        val expression: UntypedLambda,
        val isTailRecursive: Boolean
) : LambdaProgramExpression()

data class TypeDefinition(
        val name: TypeName,
        val expression: RawAlgebraicType
) : LambdaProgramExpression()

data class LambdaTypeDeclaration(
        val name: LambdaName,
        val type: Parametrised<RawType>
) : LambdaProgramExpression()