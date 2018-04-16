package thesis.preprocess.spec

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.Typed
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.TypeSignature
import thesis.preprocess.spec.parametrised.ParametrisedTrainableSpec


/**
 * Specification of eagerly evaluated expression
 *
 * @author Danil Kolikov
 */
sealed class Spec : Typed<Type> {

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
                val signature: InstanceSignature,
                val typeSignature: TypeSignature,
                override val type: Type,
                val function: Spec
        ) : Spec.Variable()
    }

    data class Object(
            override val type: Type,
            val size: Int,
            val position: Int
    ) : Spec() {

        override fun toString() = "[$type: $position]"
    }

    sealed class Function : Spec() {

        abstract val closurePointer: DataPointer

        data class Trainable(
                val instanceSignature: InstanceSignature,
                val instancePosition: Int,
                val trainableSpec: ParametrisedTrainableSpec,
                override val type: Type,
                val dataPointer: DataPointer,
                val typeParamsSize: Map<TypeVariableName, Int>
        ) : Function() {
            override val closurePointer = DataPointer.START // Assuming trainable to be defined in global scope

            override fun toString() = "[@learn: $type]"
        }

        data class Constructor(
                val name: TypeName,
                override val type: Type,
                val fromTypeSize: Int,
                val toTypeSize: Int,
                val offset: Int
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

        data class Recursive(
                val name: LambdaName,
                val body: Spec,
                override val type: Type,
                override val closurePointer: DataPointer
        ) : Function() {

            override fun toString() = "(rec: $body)"
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