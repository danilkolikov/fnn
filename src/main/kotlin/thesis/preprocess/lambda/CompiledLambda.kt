package thesis.preprocess.lambda

import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName
import java.util.*

/**
 * Executable representation of lambda-expression.
 * During execution expression is eagerly evaluated
 */
sealed class CompiledLambda : Replaceable<CompiledLambda> {

    abstract fun execute(): CompiledLambda

    interface Callable {
        fun call(arguments: List<CompiledLambda>): CompiledLambda
    }

    class Object(val name: TypeName, val data: Array<Boolean>) : CompiledLambda() {
        override fun execute() = this
        override fun replace(map: Map<String, CompiledLambda>) = this

        override fun toString() = "[$name: ${Arrays.toString(data)}]"
    }

    class ObjectFunction(
            val name: TypeName,
            val memoryRepresentation: MemoryRepresentation.Constructor
    ) : CompiledLambda(), Callable {
        override fun execute() = this
        override fun replace(map: Map<String, CompiledLambda>) = this
        override fun call(arguments: List<CompiledLambda>): CompiledLambda {
            return if (arguments.all { it is Object }) {
                val data = Array(memoryRepresentation.typeSize, { false })
                arguments
                        .filterIsInstance(Object::class.java)
                        .zip(memoryRepresentation.info.argumentOffsets)
                        .forEach { (obj, offset) ->
                            System.arraycopy(obj.data, 0, data, offset, obj.data.size)
                        }
                Object(memoryRepresentation.typeName, data)
            } else this
        }

        override fun toString() = "[$name: ... → ${memoryRepresentation.typeName}]"
    }

    class Variable(val name: String) : CompiledLambda() {
        override fun execute() = this
        override fun replace(map: Map<String, CompiledLambda>) = map[name] ?: this

        override fun toString() = name
    }

    class FunctionCall(val function: CompiledLambda, val arguments: List<CompiledLambda>) : CompiledLambda() {
        override fun execute(): CompiledLambda {
            val function = function.execute()
            val arguments = arguments.map { it.execute() }
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
        override fun execute() = AnonymousFunction(arguments, body.execute())

        override fun replace(map: Map<String, CompiledLambda>) = AnonymousFunction(arguments, body.replace(map))

        override fun call(arguments: List<CompiledLambda>): CompiledLambda {
            val replaceMap = this.arguments.zip(arguments).toMap()
            return body.replace(replaceMap)
        }

        override fun toString() = "(\\${arguments.joinToString(" ")}. $body)"
    }
}