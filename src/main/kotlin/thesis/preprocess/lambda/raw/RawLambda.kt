package thesis.preprocess.lambda.raw

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.lambda.MemoryRepresentation
import thesis.preprocess.lambda.raw.RawLambda.Object

/**
 * Callable lambda which arguments are plain array and list of functions
 *
 * @author Danil Kolikov
 */
sealed class RawLambda {

    abstract fun call(arguments: RawArguments): RawLambda

    sealed class Variable : RawLambda() {

        class Object(val type: TypeName, val positions: Pair<Int, Int>) : Variable() {
            override fun call(arguments: RawArguments) = Object(
                    type,
                    arguments.data.subList(positions.first, positions.second)
            )

            override fun toString() = "[$type: $positions]"
        }

        class Function(val name: LambdaName, val position: Int) : Variable() {
            override fun call(arguments: RawArguments) = arguments.functions[position]

            override fun toString() = "[$name: $position]"
        }
    }

    class Object(val type: TypeName, val data: List<Short>) : RawLambda() {
        override fun call(arguments: RawArguments) = this // Doesn't change after call

        override fun toString() = "[$type: $data]"
    }

    sealed class Function : RawLambda() {

        abstract fun test(arguments: RawArguments): Boolean

        protected abstract fun doCall(arguments: RawArguments): RawLambda

        override fun call(arguments: RawArguments) = if (test(arguments)) doCall(arguments) else this

        class Constructor(
                val name: TypeName,
                val memoryRepresentation: MemoryRepresentation.Constructor
        ) : Function() {

            override fun test(arguments: RawArguments) = arguments.data.size == memoryRepresentation.typeSize

            override fun doCall(arguments: RawArguments): RawLambda {
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
                        memoryRepresentation.typeName,
                        data
                )
            }

            override fun toString() = name
        }

        class Guarded(val name: LambdaName, val lambdas: List<Anonymous>) : Function() {
            override fun test(arguments: RawArguments) = lambdas.any { it.test(arguments) }

            override fun doCall(arguments: RawArguments): RawLambda {
                for (lambda in lambdas) {
                    if (lambda.test(arguments)) {
                        return lambda.call(arguments)
                    }
                }
                return this
            }

            override fun toString() = "($name = ${lambdas.joinToString(", ")})"
        }

        class Anonymous(val guards: List<Guard>, val body: RawLambda) : Function() {

            override fun test(arguments: RawArguments) = guards.all { it.match(arguments) }

            override fun doCall(arguments: RawArguments) = body.call(arguments)

            override fun toString() = body.toString()
        }
    }

    class Application(val function: RawLambda, val arguments: List<RawLambda>) : RawLambda() {
        override fun call(arguments: RawArguments): RawLambda {
            val operands = listOf(function) + this.arguments

            // Eagerly call operands, if they allow to be called
            val calledOperands = operands.map {
                when (it) {
                    is Object -> it                             // Don't call objects
                    is Variable -> it.call(arguments)           // Replace variables with actual values
                    is Application -> it.call(arguments)        // Call nested applications
                    is Function -> it.call(RawArguments.EMPTY)  // Call functions with empty arguments (to check if they are constants)
                }
            }

            var calledFunction = calledOperands.first()

            val called = calledOperands.drop(1)
            val args = mutableListOf<RawLambda>()

            // Apply function with arguments, adding them one-by-one
            // If function is applied, continue with rest of variables. Otherwise, add more arguments
            for (arg in called) {
                args.add(arg)
                val data = args.filterIsInstance(Object::class.java).flatMap { it.data }
                val functions = args.filterIsInstance(Function::class.java)
                val raw = RawArguments(data, functions)
                val result = calledFunction.call(raw)
                if (calledFunction != result) {
                    // Function applied
                    calledFunction = result
                    args.clear()
                }
            }
            return calledFunction
        }

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }
}