package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Type
import thesis.preprocess.results.InferredType
import thesis.preprocess.results.RenamedTypeDeclaration

/**
 * Inferring processor for lambda type declaration.
 * Check that all types used in declaration are already defined
 *
 * @author Danil Kolikov
 */
class TypeDeclarationInferenceProcessor(
        private val typeScope: List<InferredType>
) : Processor<List<RenamedTypeDeclaration>, List<RenamedTypeDeclaration>> {

    override fun process(data: List<RenamedTypeDeclaration>): List<RenamedTypeDeclaration> {
        data.forEach {
            val undefinedTypes = it.type.getUndefinedTypes()
            if (!undefinedTypes.isEmpty()) {
                throw UnknownTypeError(undefinedTypes)
            }
        }
        return data
    }

    private fun Type.getUndefinedTypes(): Set<String> = when (this) {
        is Type.Literal -> if (typeScope.any { it.name == name }) emptySet() else setOf(name)
        is Type.Function -> listOf(from, to).flatMap { it.getUndefinedTypes() }.toSet()
    }
}