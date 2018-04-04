package thesis.preprocess.spec

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName

/**
 * Specification of eagerly evaluated expression
 *
 * @author Danil Kolikov
 */
sealed class Spec {

    abstract val type: Type

    sealed class Variable : Spec() {
        data class Object(
                override val type: Type,
                val positions: Pair<Int, Int>
        ) : Variable() {

            override fun toString() = "[$type: $positions]"
        }

        data class Function(
                val name: LambdaName,
                override val type: Type,
                val position: Int
        ) : Variable() {

            override fun toString() = "[$name: $position]"
        }

        data class External(
                val name: LambdaName,
                override val type: Type,
                val function: Spec.Function.Guarded
        ) : Spec.Variable()
    }

    data class Object(
            override val type: Type,
            val data: List<Short>
    ) : Spec() {

        override fun toString() = "[$type: $data]"
    }

    sealed class Function : Spec() {

        abstract val closurePointer: DataPointer

        data class Trainable(
                override val type: Type,
                val fromTypeSize: Int,
                val toTypeName: TypeName,
                val toType: TypeSpec
        ) : Function() {
            override val closurePointer = DataPointer.START // Assuming trainable to be defined in global scope
        }

        data class Constructor(
                val name: TypeName,
                override val type: Type,
                val fromTypeSize: Int,
                val toTypeSize: Int,
                val offset: Int
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in global scope

            override fun toString() = name
        }

        data class Guarded(
                val name: LambdaName,
                override val type: Type,
                val cases: List<Case>
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in the global scope

            override fun toString() = "($name = ${cases.joinToString(", ")})"

            class Case(
                    val patterns: List<DataPattern>,
                    val body: Spec
            ) {
                override fun toString() = "${patterns.joinToString(" ")} -> $body"
            }
        }

        data class Anonymous(
                override val type: Type,
                val body: Spec,
                override val closurePointer: DataPointer
        ) : Function() {

            override fun toString() = "($body)"
        }
    }

    data class Application(
            override val type: Type,
            val operands: List<Spec>,
            val call: List<Int>,
            val constants: List<Int>,
            val data: List<Int>,
            val functions: List<Int>
    ) : Spec() {

        override fun toString() = "(${operands.joinToString(" ")})"
    }
}