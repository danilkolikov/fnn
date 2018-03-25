package thesis.preprocess.lambda.raw

import thesis.preprocess.lambda.MemoryRepresentation

/**
 * Guard for object. Throws exception if object or variable doesn't match expected pattern
 *
 * @author Danil Kolikov
 */
sealed class Guard {

    abstract fun match(arguments: RawArguments): Boolean

    sealed class Data : Guard() {

        abstract val positions: Pair<Int, Int>

        abstract fun match(data: List<Short>): Boolean

        override fun match(arguments: RawArguments) = positions.second <= arguments.data.size && match(
                arguments.data.subList(positions.first, positions.second)
        )

        data class Variable(
                override val positions: Pair<Int, Int>
        ) : Data() {

            override fun match(data: List<Short>) = data.any { it == 1.toShort() }
        }

        data class Object(
                override val positions: Pair<Int, Int>,
                val memory: MemoryRepresentation.Object
        ) : Data() {

            override fun match(data: List<Short>) = data == memory.representation
        }
    }

    data class Function(val position: Int) : Guard() {

        override fun match(arguments: RawArguments) = position < arguments.functions.size
    }
}