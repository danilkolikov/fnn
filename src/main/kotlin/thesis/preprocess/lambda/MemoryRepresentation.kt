package thesis.preprocess.lambda

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.memory.TypeMemoryInformation

sealed class MemoryRepresentation {
    abstract val typeName: TypeName
    abstract val typeSize: Int
    abstract val info: TypeMemoryInformation.ConstructorInformation

    data class Object(
            override val typeName: TypeName,
            override val info: TypeMemoryInformation.ConstructorInformation,
            override val typeSize: Int,
            val representation: List<Short>
    ) : MemoryRepresentation()

    data class Constructor(
            override val typeName: TypeName,
            override val info: TypeMemoryInformation.ConstructorInformation,
            override val typeSize: Int
    ) : MemoryRepresentation()
}