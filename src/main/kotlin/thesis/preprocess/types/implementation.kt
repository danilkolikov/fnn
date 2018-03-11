/**
 * Implementation of the type inference algorithm
 */
package thesis.preprocess.types

import thesis.preprocess.ast.*
import thesis.preprocess.expressions.*
import thesis.preprocess.types.algorithms.*
import thesis.utils.AlgebraicTerm

fun LambdaProgram.inferType(): InferenceContext {
    val context = InferenceContext()
    val algebraicTypeInferenceAlgorithm = AlgebraicTypeInferenceAlgorithm()
    val lambdaTermsTypeInferenceAlgorithm = LambdaTermsInferenceAlgorithm()
    val lambdaTypeDeclarationProcessor = LambdaTypeDeclarationProcessor()

    expressions.forEach {
        when (it) {
            is TypeDefinition -> algebraicTypeInferenceAlgorithm.processExpression(context, it)
            is LambdaDefinition -> lambdaTermsTypeInferenceAlgorithm.processExpression(context, it)
            is LambdaTypeDeclaration -> lambdaTypeDeclarationProcessor.processExpression(context, it)
        }
    }

    return context
}

data class InferenceContext(
        override val typeDefinitions: MutableMap<TypeName, AlgebraicType> = mutableMapOf(),
        override val lambdaDefinitions: MutableMap<LambdaName, Lambda> = mutableMapOf(),

        override val typeScope: MutableMap<TypeName, AlgebraicTerm> = mutableMapOf(),
        override val expressionScope: MutableMap<String, AlgebraicTerm> = mutableMapOf(),

        override val nameGenerator: NameGenerator = NameGenerator(),
        override val nameMap: MutableMap<LambdaName, LambdaName> = mutableMapOf()
) : AlgebraicTypeInferenceContext, LambdaTermsTypeInferenceContext {
    val types: Map<LambdaName, Type>
        get() = expressionScope
                .map { (type, value) -> type to value.toSimpleType() }
                .toMap()

}