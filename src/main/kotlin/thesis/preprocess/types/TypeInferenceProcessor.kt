package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.results.InferredExpressions
import thesis.preprocess.results.SortedExpressions
import thesis.utils.NameGenerator

/**
 * Infers types for expressions of lambda program
 *
 * @author Danil Kolikov
 */
class TypeInferenceProcessor(
        private val nameGenerator: NameGenerator
) : Processor<SortedExpressions, InferredExpressions> {
    override fun process(data: SortedExpressions): InferredExpressions {
        val typeInfo = AlgebraicTypeInferenceProcessor()
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