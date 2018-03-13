/**
 * Algorithm of inference of types of lambda type declarations
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.LambdaTypeDeclaration
import thesis.preprocess.expressions.Type
import thesis.preprocess.types.TypeContext
import thesis.preprocess.types.TypeContextUpdater
import thesis.preprocess.types.UnknownTypeError
import thesis.preprocess.types.toAlgebraicTerm

class LambdaTypeDeclarationContextUpdater(
        override val typeContext: LambdaTermsTypeContext
) : TypeContextUpdater<TypeContext, LambdaTypeDeclaration> {

    override fun updateContext(expression: LambdaTypeDeclaration) {
        val (name, type) = expression
        val undefinedTypes = type.getUndefinedTypes()
        if (!undefinedTypes.isEmpty()) {
            throw UnknownTypeError(undefinedTypes, typeContext)
        }
        typeContext.expressionScope[name] = type.toAlgebraicTerm()
    }

    private fun Type.getUndefinedTypes(): Set<String> = when (this) {
        is Type.Literal -> if (typeContext.typeScope.containsKey(name)) emptySet() else setOf(name)
        is Type.Function -> listOf(from, to).flatMap { it.getUndefinedTypes() }.toSet()
    }
}