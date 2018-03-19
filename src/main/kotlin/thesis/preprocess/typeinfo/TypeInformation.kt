package thesis.preprocess.typeinfo

import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName

/**
 * Information about representation of type in memory
 */
class TypeInformation(
        val name: String,
        constructors: List<Pair<TypeName, Type>>,
        informationScope: Map<TypeName, TypeInformation>
) {

    val constructors: Map<TypeName, ConstructorInformation>
    val typeSize: Int

    data class ConstructorInformation(val offset: Int, val argumentOffsets: List<Int>)

    override fun toString(): String {
        return "TypeInformation(name='$name', constructors=$constructors, typeSize=$typeSize)"
    }

    init {
        val constructorsMap = mutableMapOf<TypeName, ConstructorInformation>()
        var currentOffset = 0
        for ((constructorName, type) in constructors) {
            val arguments = mutableListOf<TypeName>()
            var curType = type
            while (curType is Type.Function) {
                arguments.add((curType.from as Type.Literal).name)
                curType = curType.to
            }
            if (arguments.isEmpty()) {
                // Type literal
                constructorsMap[constructorName] = ConstructorInformation(
                        currentOffset++,
                        emptyList()
                )
                continue
            }
            val argumentOffsets = mutableListOf<Int>()
            val startOffset = currentOffset
            for (argument in arguments) {
                val size = informationScope[argument]!!.typeSize
                argumentOffsets.add(currentOffset)
                currentOffset += size
            }
            constructorsMap[constructorName] = ConstructorInformation(
                    startOffset,
                    argumentOffsets
            )
        }
        this.constructors = constructorsMap
        this.typeSize = currentOffset
    }
}
