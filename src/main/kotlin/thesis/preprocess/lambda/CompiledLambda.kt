package thesis.preprocess.lambda

import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName
import java.util.*

/**
 * Executable representation of lambda-expression.
 * During execution expression is eagerly evaluated
 */
sealed class CompiledLambda : Replaceable<CompiledLambda> {

    abstract fun reduce(): CompiledLambda

    interface Callable {
        fun call(arguments: List<CompiledLambda>): CompiledLambda
    }

    class Object(val type: TypeName, val data: Array<Short>) : CompiledLambda() {
        override fun reduce() = this
        override fun replace(map: Map<String, CompiledLambda>) = this

        override fun toString() = "[$type: ${Arrays.toString(data)}]"
    }

    class ObjectFunction(
            val name: TypeName,
            val memoryRepresentation: MemoryRepresentation.Constructor
    ) : CompiledLambda(), Callable {
        override fun reduce() = this
        override fun replace(map: Map<String, CompiledLambda>) = this
        override fun call(arguments: List<CompiledLambda>): CompiledLambda {
            return if (arguments.all { it is Object }) {
                val data = Array(memoryRepresentation.typeSize, { 0.toShort() })
                arguments
                        .filterIsInstance(Object::class.java)
                        .zip(memoryRepresentation.info.argumentOffsets)
                        .forEach { (obj, info) ->
                            System.arraycopy(obj.data, 0, data, info.offset, obj.data.size)
                        }
                Object(memoryRepresentation.typeName, data)
            } else this
        }

        override fun toString() = "[$name: ... → ${memoryRepresentation.typeName}]"
    }

    class GuardedFunction(
            val lambdas: List<CompiledLambdaWithPatterns>
    ) : CompiledLambda(), Callable {
        // Every variable here is assumed to be bound
        override fun replace(map: Map<String, CompiledLambda>) = this

        override fun reduce() = GuardedFunction(
                lambdas.map { CompiledLambdaWithPatterns(it.patterns, it.lambda.reduce()) }
        )

        override fun call(arguments: List<CompiledLambda>): CompiledLambda {
            for ((patterns, lambda) in lambdas) {
                if (arguments.size < patterns.size) {
                    continue
                }
                val toMatch = arguments.take(patterns.size)
                val rest = arguments.drop(patterns.size)
                val matched = patterns.zip(toMatch) { pattern, arg -> pattern.match(arg) }
                if (matched.any { it == null }) {
                    continue
                }
                val replaceMap = matched.filterNotNull().flatMap { it.map { it.toPair() } }.toMap()
                val argumentsApplied = lambda.replace(replaceMap).reduce()
                return if (rest.isEmpty()) argumentsApplied else FunctionCall(argumentsApplied, rest).reduce()
            }
            return this
        }

        override fun toString() = lambdas.map { "(${it.patterns.joinToString(" ")} = ${it.lambda}" }
                .joinToString("; ")
    }

    class Variable(val name: String) : CompiledLambda() {
        override fun reduce() = this
        override fun replace(map: Map<String, CompiledLambda>) = map[name] ?: this

        override fun toString() = name
    }

    class FunctionCall(val function: CompiledLambda, val arguments: List<CompiledLambda>) : CompiledLambda() {
        override fun reduce(): CompiledLambda {
            val function = function.reduce()
            val arguments = arguments.map { it.reduce() }
            return if (function is Callable)
                function.call(arguments)
            else
                FunctionCall(function, arguments)
        }

        override fun replace(map: Map<String, CompiledLambda>) = FunctionCall(
                function.replace(map),
                arguments.map { it.replace(map) }
        )

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }

    class AnonymousFunction(
            val arguments: List<String>,
            val body: CompiledLambda
    ) : CompiledLambda(), Callable {
        override fun reduce() = AnonymousFunction(arguments, body.reduce())

        override fun replace(map: Map<String, CompiledLambda>) = AnonymousFunction(arguments, body.replace(map))

        override fun call(arguments: List<CompiledLambda>): CompiledLambda {
            val replaceMap = this.arguments.zip(arguments).toMap()
            return body.replace(replaceMap).reduce()
        }

        override fun toString() = "(\\${arguments.joinToString(" ")}. $body)"
    }
}