/**
 * Implementation of the type inference algorithm
 */
package thesis.preprocess.types

import thesis.preprocess.ast.*
import thesis.preprocess.expressions.*
import thesis.preprocess.types.algorithms.*
import thesis.utils.AlgebraicTerm

fun LambdaProgram.inferType(): Context {
    val context = Context()
    val algebraicTypeInferenceAlgorithm = AlgebraicTypeInferenceAlgorithm(context)
    val lambdaTermsTypeInferenceAlgorithm = LambdaTermsInferenceAlgorithm(context)
    val lambdaTypeDeclarationContextUpdater = LambdaTypeDeclarationContextUpdater(context)

    expressions.forEach {
        when (it) {
            is TypeDefinition -> algebraicTypeInferenceAlgorithm.updateContext(it)
            is LambdaDefinition -> lambdaTermsTypeInferenceAlgorithm.updateContext(it)
            is LambdaTypeDeclaration -> lambdaTypeDeclarationContextUpdater.updateContext(it)
        }
    }

    return context
}

data class Context(
        override val typeDefinitions: MutableMap<TypeName, AlgebraicType> = mutableMapOf(),
        override val lambdaDefinitions: MutableMap<LambdaName, Lambda> = mutableMapOf(),

        override val typeConstructors: MutableMap<TypeName, AlgebraicTerm> = mutableMapOf(),
        override val typeScope: MutableMap<TypeName, AlgebraicTerm> = mutableMapOf(),
        override val expressionScope: MutableMap<String, AlgebraicTerm> = mutableMapOf(),

        override val nameGenerator: NameGenerator = NameGenerator()
) : AlgebraicTypeContext, LambdaTermsTypeContext {
    val types: Map<LambdaName, Type>
        get() = (typeConstructors + expressionScope)
                .map { (type, value) -> type to value.toSimpleType() }
                .toMap()

}