package thesis.preprocess

import thesis.preprocess.ast.LambdaDefinition
import thesis.preprocess.ast.LambdaProgram
import thesis.preprocess.ast.LambdaTypeDeclaration
import thesis.preprocess.ast.TypeDefinition
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.types.algorithms.AlgebraicTypeInferenceAlgorithm
import thesis.preprocess.types.algorithms.LambdaTermsInferenceAlgorithm
import thesis.preprocess.types.algorithms.LambdaTypeDeclarationContextUpdater

interface ContextBuilder<out C> {
    val context: C

    val typeDefinitionContextUpdater: ContextUpdaterByLocal<C, AlgebraicType>
    val lambdaDefinitionContextUpdater: ContextUpdaterByLocal<C, Lambda>
}

class Preprocessor(val contextBuilders: List<ContextBuilder<*>>) {

    fun process(lambdaProgram: LambdaProgram): Context {
        val context = Context()
        val algebraicTypeInferenceAlgorithm = AlgebraicTypeInferenceAlgorithm(
                context,
                contextBuilders.map { it.typeDefinitionContextUpdater }
        )
        val lambdaTermsTypeInferenceAlgorithm = LambdaTermsInferenceAlgorithm(
                context,
                contextBuilders.map { it.lambdaDefinitionContextUpdater }
        )
        val lambdaTypeDeclarationContextUpdater = LambdaTypeDeclarationContextUpdater(context)

        lambdaProgram.expressions.forEach {
            when (it) {
                is TypeDefinition -> algebraicTypeInferenceAlgorithm.updateContext(it)
                is LambdaDefinition -> lambdaTermsTypeInferenceAlgorithm.updateContext(it)
                is LambdaTypeDeclaration -> lambdaTypeDeclarationContextUpdater.updateContext(it)
            }
        }

        return context
    }
}
