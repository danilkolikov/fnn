/**
 * Algorithm of inference of types of lambda type declarations
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.LambdaTypeDeclaration
import thesis.preprocess.types.*

class LambdaTypeDeclarationProcessor : LambdaProgramExpressionProcessor<TypeInferenceContext, LambdaTypeDeclaration> {
    override fun processExpression(
            context: TypeInferenceContext,
            expression: LambdaTypeDeclaration
    ): TypeInferenceContext {
        val (name, type) = expression
        val undefinedTypes = type.getUndefinedTypes(context)
        if (!undefinedTypes.isEmpty()) {
            throw UnknownTypeError(undefinedTypes, context)
        }
        context.expressionScope[name] = type.toAlgebraicTerm()
        return context
    }

    private fun SimpleType.getUndefinedTypes(context: TypeInferenceContext): Set<String> = when (this) {
        is TypeTerminal -> if (context.typeScope.containsKey(name)) emptySet() else setOf(name)
        is TypeFunction -> listOf(from, to).flatMap { it.getUndefinedTypes(context) }.toSet()
    }
}