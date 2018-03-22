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
        val memoryInformation = mutableMapOf<TypeName, InMemoryType>()
        data.typeDefinitions.forEach { name, value ->
            val scope = memoryInformation.mapValues { (_, type) -> type.memoryInfo }
            val typeMemoryInformation = TypeMemoryInformation(
                    name,
                    value.constructors,
                    scope
            )
            memoryInformation[name] = InMemoryTypeImpl(value, typeMemoryInformation)
        }
        return InMemoryExpressions(
                memoryInformation,
                data.lambdaDefinitions
        )
    }
}