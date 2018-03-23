package thesis.preprocess.memory

import thesis.preprocess.Processor
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.results.InMemoryExpressions
import thesis.preprocess.results.InMemoryType
import thesis.preprocess.results.InMemoryTypeImpl
import thesis.preprocess.results.InferredExpressions

/**
 * Extracts memory-specific information from type definitions
 *
 * @author Danil Kolikov
 */
class TypeMemoryProcessor : Processor<InferredExpressions, InMemoryExpressions> {

    override fun process(data: InferredExpressions): InMemoryExpressions {
        val memoryInformation = mutableListOf<InMemoryType>()
        val scope = mutableMapOf<TypeName, TypeMemoryInformation>()
        data.typeDefinitions.map { value ->
            val typeMemoryInformation = TypeMemoryInformation(
                    value.name,
                    value.constructors,
                    scope
            )
            scope[value.name] = typeMemoryInformation
            memoryInformation.add(InMemoryTypeImpl(value, typeMemoryInformation))
        }
        return InMemoryExpressions(
                memoryInformation,
                data.lambdaDefinitions
        )
    }
}