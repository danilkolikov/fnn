package thesis.preprocess.lambda.raw

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.lambda.MemoryRepresentation
import thesis.preprocess.lambda.raw.RawLambda.Object

/**
 * Callable lambda which arguments are plain array and list of functions
 *
 * @author Danil Kolikov
 */
sealed class RawLambda {

    abstract val type: Type

    abstract val closurePointer: DataPointer

    abstract fun call(arguments: RawArguments): RawLambda

    sealed class Variable : RawLambda() {

        abstract fun resolve(arguments: RawArguments): RawLambda

        override fun call(arguments: RawArguments) = resolve(arguments)

        class Object(
                override val type: Type,
                val positions: Pair<Int, Int>,
                override val closurePointer: DataPointer
        ) : Variable() {

            override fun resolve(arguments: RawArguments) = Object(
                    type,
                    arguments.data.subList(positions.first, positions.second)
            )

            override fun toString() = "[$type: $positions]"
        }

        class Function(
                val name: LambdaName,
                override val type: Type,
                val position: Int,
                override val closurePointer: DataPointer
        ) : Variable() {

            override fun resolve(arguments: RawArguments) = arguments.functions[position]

            override fun toString() = "[$name: $position]"
        }
    }

    class Object(override val type: Type, val data: List<Short>) : RawLambda() {

        override val closurePointer = DataPointer.START // Doesn't have any closure

        override fun call(arguments: RawArguments) = this // Doesn't change after call

        override fun toString() = "[$type: $data]"
    }

    sealed class Function : RawLambda() {

        class Constructor(
                val name: TypeName,
                override val type: Type,
                val memoryRepresentation: MemoryRepresentation.Constructor
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in global scope

            override fun call(arguments: RawArguments): RawLambda {
                val offset = memoryRepresentation.info.offset
                val data = List(memoryRepresentation.typeSize, {
                    if (it < offset) {
                        return@List 0.toShort()
                    }
                    if (it > offset + memoryRepresentation.typeSize) {
                        return@List 0.toShort()
                    }
                    return@List arguments.data[it - offset]
                })

                return Object(
                        type.getResultType(),
                        data
                )
            }

            override fun toString() = name
        }

        class Guarded(
                val name: LambdaName,
                override val type: Type,
                val lambdas: List<Case>
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in global scope

            override fun call(arguments: RawArguments): RawLambda {
                for (lambda in lambdas) {
                    if (lambda.test(arguments)) {
                        return lambda.body.call(arguments)
                    }
                }
                return this
            }

            override fun toString() = "($name = ${lambdas.joinToString(", ")})"

            class Case(
                    val guards: List<Guard>,
                    val body: RawLambda
            ) {
                fun test(arguments: RawArguments) = guards.all { it.match(arguments) }

                override fun toString() = body.toString()
            }
        }

        class Anonymous(
                override val type: Type,
                val body: RawLambda,
                override val closurePointer: DataPointer
        ) : Function() {
            override fun call (arguments: RawArguments) = body.call(arguments)
        }
    }

    class Application(
            override val type: Type,
            val function: RawLambda,
            val arguments: List<RawLambda>,
            override val closurePointer: DataPointer
    ) : RawLambda() {
        override fun call(arguments: RawArguments): RawLambda {
            // Resolve variables and call applications
            val resolved = (listOf(function) + this.arguments).map {
                when (it) {
                    is Variable -> it.resolve(arguments)
                    is Application -> it.call(arguments)
                    else -> it
                }
            }

            val func = resolved.first()

            val args = resolved.drop(1)
            val data = args.filterIsInstance(Object::class.java).flatMap { it.data }
            val functions = args.filterIsInstance(Function::class.java)
            val newArgs = RawArguments(data, functions)
            val raw = arguments.getArgumentsBefore(func.closurePointer).append(newArgs)
            return func.call(raw)
        }

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }
}