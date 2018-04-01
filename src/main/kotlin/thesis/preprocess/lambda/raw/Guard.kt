package thesis.preprocess.lambda.raw

import thesis.preprocess.lambda.MemoryRepresentation

/**
 * Guard for object. Throws exception if object or variable doesn't match expected pattern
 *
 * @author Danil Kolikov
 */
sealed class Guard {

    abstract val positions: Pair<Int, Int>

    abstract fun match(data: List<Short>): Boolean

    fun match(arguments: RawArguments) = positions.second <= arguments.data.size && match(
            arguments.data.subList(positions.first, positions.second)
    )

    data class Variable(
            override val positions: Pair<Int, Int>
    ) : Guard() {

        override fun match(data: List<Short>) = data.any { it == 1.toShort() }
    }

    data class Object(
            override val positions: Pair<Int, Int>,
            val memory: MemoryRepresentation.Object
    ) : Guard() {

        override fun match(data: List<Short>) = data == memory.representation
    }
}