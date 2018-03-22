package thesis.preprocess.lambda

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.memory.TypeMemoryInformation

sealed class MemoryRepresentation {
    data class Object(
            val typeName: TypeName,
            val representation: Array<Boolean>
    ) : MemoryRepresentation()
    data class Constructor(
            val typeName: TypeName,
            val typeSize: Int,
            val info: TypeMemoryInformation.ConstructorInformation
    ) : MemoryRepresentation()
}