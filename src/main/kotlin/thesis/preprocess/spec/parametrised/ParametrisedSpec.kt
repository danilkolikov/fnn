package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.Typed
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.spec.ParametrizedPattern

/**
 * Parametrised specification of eagerly evaluated expression
 *
 * @author Danil Kolikov
 */
sealed class ParametrisedSpec : Typed<Parametrised<Type>> {

    abstract val instancePath: List<LambdaName>

    fun isInstantiated() = type.isInstantiated

    data class Variable(
            val name: LambdaName,
            override val instancePath: List<LambdaName>,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override fun toString() = name
    }

    data class Object(
            val name: TypeName,
            val algebraicType: AlgebraicType,
            val position: Int,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override val instancePath = listOf(name)

        override fun toString() = "[$name: $type]"
    }

    sealed class Function : ParametrisedSpec() {

        data class Trainable(
                val instanceSignature: InstanceSignature,
                val trainableSpec: ParametrisedTrainableSpec,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {

            override fun toString() = "[@learn : $type]"
        }

        data class Constructor(
                val name: TypeName,
                val algebraicType: AlgebraicType,
                val position: Int,
                override val type: Parametrised<Type>
        ) : Function() {

            override val instancePath = listOf(name)

            override fun toString() = "[name : $type]"
        }

        data class Guarded(
                override val instancePath: InstanceSignature,
                val cases: List<Case>,
                override val type: Parametrised<Type>
        ) : Function() {

            override fun toString() = "($instancePath: ${cases.joinToString("; ")})"

            class Case(
                    val patterns: List<ParametrizedPattern>,
                    val body: ParametrisedSpec
            ) {

                override fun toString() = "${patterns.joinToString(" ")} -> $body"
            }
        }

        data class Polymorphic(
                val name: LambdaName,
                val expression: ParametrisedSpec,
                val instanceTypeParams: Map<TypeVariableName, Type>,
                val signature: InstanceSignature,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {
            override fun toString() = "(poly: $expression)"
        }

        data class Anonymous(
                val arguments: LinkedHashMap<LambdaName, Parametrised<Type>>,
                val body: ParametrisedSpec,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {

            override fun toString() = "($body)"
        }

        data class Recursive(
                val argument: LambdaName,
                val body: ParametrisedSpec,
                val isTailRecursive: Boolean,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {

            override fun toString() = "(rec: $body)"
        }
    }

    data class Application(
            val operands: List<ParametrisedSpec>,
            override val instancePath: List<LambdaName>,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override fun toString() = "(${operands.joinToString(" ")})"
    }

    data class LetAbstraction(
            val expression: ParametrisedSpec,
            val bindings: List<LambdaName>,
            override val instancePath: InstanceSignature,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override fun toString() = "(let: $expression)"
    }
}