package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.results.InferredType

/**
 * Inferring processor for lambda type declaration.
 * Check that all types used in declaration are already defined
 *
 * @author Danil Kolikov
 */
class TypeDeclarationInferenceProcessor(
        private val typeScope: Map<TypeName, InferredType>
) : Processor<Map<LambdaName, Type>, Map<LambdaName, Type>> {

    override fun process(data: Map<LambdaName, Type>): Map<LambdaName, Type> {
        data.forEach {_, type ->
            val undefinedTypes = type.getUndefinedTypes()
            if (!undefinedTypes.isEmpty()) {
                throw UnknownTypeError(undefinedTypes)
            }
        }
        return data
    }

    private fun Type.getUndefinedTypes(): Set<String> = when (this) {
        is Type.Literal -> if (typeScope.containsKey(name)) emptySet() else setOf(name)
        is Type.Function -> listOf(from, to).flatMap { it.getUndefinedTypes() }.toSet()
    }
}