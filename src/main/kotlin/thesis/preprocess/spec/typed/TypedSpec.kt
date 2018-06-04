package thesis.preprocess.spec.typed

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.Typed
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.parametrised.ParametrisedTrainableSpec


/**
 * Specification of eagerly evaluated expression
 *
 * @author Danil Kolikov
 */
sealed class TypedSpec : Typed<Type> {

    sealed class Variable : TypedSpec() {

        data class Object(
                override val type: Type,
                val position: Int
        ) : Variable() {

            override fun toString() = "[$type: $position]"
        }

        data class Function(
                val name: LambdaName,
                override val type: Type,
                val position: Int
        ) : Variable() {

            override fun toString() = "[$name: $position]"
        }

        data class External(
                val signature: InstanceSignature,
                override val type: Type
        ) : TypedSpec.Variable()
    }

    data class Object(
            override val type: Type,
            val toType: Type.Application,
            val position: Int
    ) : TypedSpec() {

        override fun toString() = "[$type: $position]"
    }

    sealed class Function : TypedSpec() {

        abstract val closurePointer: DataPointer

        data class Trainable(
                val instanceSignature: InstanceSignature,
                val trainableTypedSpec: ParametrisedTrainableSpec,
                override val type: Type,
                val dataPointer: DataPointer
        ) : Function() {
            override val closurePointer = DataPointer.START // Assuming trainable to be defined in global scope

            override fun toString() = "[@learn: $type]"
        }

        data class Constructor(
                val name: TypeName,
                override val type: Type,
                val toType: Type.Application,
                val position: Int
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in global scope

            override fun toString() = "[$name: $type]"
        }

        data class Guarded(
                val name: LambdaName,
                override val type: Type,
                val cases: List<Case>
        ) : Function() {

            override val closurePointer = DataPointer.START // Defined in the global scope

            override fun toString() = "($name = ${cases.joinToString(", ")})"

            class Case(
                    val pattern: PatternInstance,
                    val body: TypedSpec
            ) {

                override fun toString() = "$pattern -> $body"
            }
        }

        data class Anonymous(
                override val type: Type,
                val body: TypedSpec,
                override val closurePointer: DataPointer
        ) : Function() {

            override fun toString() = "($body)"
        }

        data class Recursive(
                val name: LambdaName,
                val body: TypedSpec,
                override val type: Type,
                override val closurePointer: DataPointer
        ) : Function() {

            override fun toString() = "(rec: $body)"
        }
    }

    data class Application(
            override val type: Type,
            val operands: List<TypedSpec>,
            val call: List<Int>,
            val constants: List<Int>,
            val data: List<Int>,
            val functions: List<Int>
    ) : TypedSpec() {

        override fun toString() = "(${operands.joinToString(" ")})"
    }
}