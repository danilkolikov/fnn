package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.results.InferredExpressions
import thesis.preprocess.results.RenamedExpressions

/**
 * Infers types for expressions of lambda program
 *
 * @author Danil Kolikov
 */
class TypeInferenceProcessor(
        private val nameGenerator: NameGenerator
) : Processor<RenamedExpressions, InferredExpressions> {
    override fun process(data: RenamedExpressions): InferredExpressions {
        val typeInfo = AlgebraicTypeInferenceProcessor(nameGenerator)
                .process(data.typeDefinitions)
        val lambdaDeclarations = TypeDeclarationInferenceProcessor(typeInfo)
                .process(data.typeDeclarations)
        val lambdaTypes = LambdaInferenceProcessor(nameGenerator, typeInfo, lambdaDeclarations)
                .process(data.lambdaDefinitions)
        return InferredExpressions(
                typeInfo,
                lambdaTypes
        )
    }
}