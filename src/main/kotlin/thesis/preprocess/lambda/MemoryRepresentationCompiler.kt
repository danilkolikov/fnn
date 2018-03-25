package thesis.preprocess.lambda

import thesis.preprocess.Processor
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.results.InMemoryType

class MemoryRepresentationCompiler : Processor<List<InMemoryType>, Map<TypeName, MemoryRepresentation>> {
    override fun process(data: List<InMemoryType>): Map<TypeName, MemoryRepresentation> {
        val constructors = mutableMapOf<TypeName, MemoryRepresentation>()
        data.forEach { value ->
            val name = value.name
            val memoryInfo = value.memoryInfo
            memoryInfo.constructors.map { info ->
                info.name to if (info.argumentOffsets.isEmpty()) {
                    // It's an object
                    val representation = List(
                            memoryInfo.typeSize,
                            { (if (it == info.offset) 1 else 0).toShort() }
                    )
                    MemoryRepresentation.Object(name, info, memoryInfo.typeSize, representation)
                } else {
                    // It's a function
                    MemoryRepresentation.Constructor(name, info, memoryInfo.typeSize)
                }
            }.forEach { (constructorName, info) ->
                constructors[constructorName] = info
            }
        }
        return constructors
    }
}