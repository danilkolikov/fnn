package thesis.preprocess.memory

import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName

/**
 * Information about representation of type in memory
 */
class TypeMemoryInformation(
        val name: String,
        constructors: Map<TypeName, Type>,
        informationScope: Map<TypeName, TypeMemoryInformation>
) {

    val constructors: List<ConstructorInformation>
    val typeSize: Int

    data class ConstructorInformation(
            val name: TypeName,
            val offset: Int,
            val argumentOffsets: List<ArgumentInformation>
    )

    data class ArgumentInformation(
            val type: TypeName,
            val size: Int,
            val offset: Int
    )

    override fun toString(): String {
        return "TypeMemoryInformation(name='$name', constructors=$constructors, typeSize=$typeSize)"
    }

    init {
        val constructorsList = mutableListOf<ConstructorInformation>()
        var currentOffset = 0
        for ((constructorName, type) in constructors) {
            val arguments = mutableListOf<TypeName>()
            var curType = type
            while (curType is Type.Function) {
                arguments.add((curType.from as Type.Literal).name)
                curType = curType.to
            }
            val constructorInformation = if (arguments.isEmpty()) {
                // Type literal
                ConstructorInformation(
                        constructorName,
                        currentOffset++,
                        emptyList()
                )
            } else {
                val argumentOffsets = mutableListOf<ArgumentInformation>()
                val startOffset = currentOffset
                for (argument in arguments) {
                    val size = informationScope[argument]!!.typeSize
                    argumentOffsets.add(ArgumentInformation(
                            argument,
                            size,
                            currentOffset
                    ))
                    currentOffset += size
                }
                ConstructorInformation(
                        constructorName,
                        startOffset,
                        argumentOffsets
                )
            }
            constructorsList.add(constructorInformation)

        }
        this.constructors = constructorsList
        this.typeSize = currentOffset
    }
}
