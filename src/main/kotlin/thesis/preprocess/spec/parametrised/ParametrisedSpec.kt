package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.lambda.Typed
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.instantiate
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.Instances
import thesis.preprocess.results.TypeSignature
import thesis.preprocess.spec.ParametrizedPattern
import thesis.preprocess.spec.instantiate

/**
 * Specification of eagerly evaluated expression
 *
 * @author Danil Kolikov
 */
sealed class ParametrisedSpec : Typed<Parametrised<Type>> {

    abstract val instancePath: List<LambdaName>

    abstract fun instantiate(
            typeParams: Map<TypeVariableName, Type>,
            instances: Instances<ParametrisedSpec>
    ): ParametrisedSpec

    fun isInstantiated() = type.isInstantiated

    data class Variable(
            val name: LambdaName,
            override val instancePath: List<LambdaName>,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {
        override fun instantiate(
                typeParams: Map<TypeVariableName, Type>,
                instances: Instances<ParametrisedSpec>
        ) = Variable(
                name,
                instancePath,
                type.instantiate(typeParams)
        )
    }

    data class Object(
            val name: TypeName,
            val algebraicType: AlgebraicType,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override val instancePath = listOf(name)

        override fun instantiate(
                typeParams: Map<TypeVariableName, Type>,
                instances: Instances<ParametrisedSpec>
        ) = Object(
                name,
                algebraicType,
                type.instantiate(typeParams)
        )

        override fun toString() = "[$name: $type]"
    }

    sealed class Function : ParametrisedSpec() {

        data class Trainable(
                val instanceSignature: InstanceSignature,
                val instancePosition: Int,
                val trainableSpec: ParametrisedTrainableSpec,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {
            override fun instantiate(
                    typeParams: Map<TypeVariableName, Type>,
                    instances: Instances<ParametrisedSpec>
            ) = Trainable(
                    instanceSignature,
                    instancePosition,
                    trainableSpec,
                    instancePath,
                    type.instantiate(typeParams)
            )
        }

        data class Constructor(
                val name: TypeName,
                val algebraicType: AlgebraicType,
                override val type: Parametrised<Type>
        ) : Function() {

            override val instancePath = listOf(name)

            override fun instantiate(
                    typeParams: Map<TypeVariableName, Type>,
                    instances: Instances<ParametrisedSpec>
            ) = Constructor(
                    name,
                    algebraicType,
                    type.instantiate(typeParams)
            )

            override fun toString() = name
        }

        data class Guarded(
                val name: LambdaName,
                val cases: List<Case>,
                override val type: Parametrised<Type>
        ) : Function() {

            override val instancePath = listOf(name)

            override fun instantiate(
                    typeParams: Map<TypeVariableName, Type>,
                    instances: Instances<ParametrisedSpec>
            ) = Guarded(
                    name,
                    cases.map { it.instantiate(typeParams, instances) },
                    type.instantiate(typeParams)
            )

            override fun toString() = "($name = ${cases.joinToString(", ")})"

            class Case(
                    val patterns: List<ParametrizedPattern>,
                    val body: ParametrisedSpec
            ) {

                fun instantiate(
                        typeParams: Map<TypeVariableName, Type>,
                        instances: Instances<ParametrisedSpec>
                ) = Case(
                        patterns.map { it.instantiate(typeParams) },
                        body.instantiate(typeParams, instances)
                )

                override fun toString() = "${patterns.joinToString(" ")} -> $body"
            }
        }

        data class Polymorphic(
                val name: LambdaName,
                val expression: ParametrisedSpec,
                val instanceTypeParams: Map<TypeVariableName, Type>,
                val signature: InstanceSignature,
                val typeSignature: TypeSignature,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {
            override fun instantiate(
                    typeParams: Map<TypeVariableName, Type>,
                    instances: Instances<ParametrisedSpec>
            ): ParametrisedSpec {
                val newTypeSignature = typeSignature.map {
                    it.replace(typeParams.mapValues { (_, v) -> v.toRaw() })
                }
                val instance = instances[signature, newTypeSignature] ?: expression.instantiate(typeParams, instances)
                instances.putIfAbsent(signature, newTypeSignature, instance)
                return Polymorphic(
                        name,
                        instance,
                        instanceTypeParams + typeParams,
                        signature,
                        newTypeSignature,
                        instancePath,
                        instance.type
                )
            }
        }

        data class Anonymous(
                val arguments: LinkedHashMap<LambdaName, Parametrised<Type>>,
                val body: ParametrisedSpec,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {

            override fun instantiate(typeParams: Map<TypeVariableName, Type>, instances: Instances<ParametrisedSpec>) = Anonymous(
                    arguments
                            .map { (name, type) -> name to type.instantiate(typeParams) }
                            .toMap(LinkedHashMap()),
                    body.instantiate(typeParams, instances),
                    instancePath,
                    type.instantiate(typeParams)
            )

            override fun toString() = "($body)"
        }

        data class Recursive(
                val argument: LambdaName,
                val body: ParametrisedSpec,
                override val instancePath: List<LambdaName>,
                override val type: Parametrised<Type>
        ) : Function() {
            override fun instantiate(typeParams: Map<TypeVariableName, Type>, instances: Instances<ParametrisedSpec>) = Recursive(
                    argument,
                    body.instantiate(typeParams, instances),
                    instancePath,
                    type.instantiate(typeParams)
            )
        }
    }

    data class Application(
            val operands: List<ParametrisedSpec>,
            override val instancePath: List<LambdaName>,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {

        override fun instantiate(
                typeParams: Map<TypeVariableName, Type>,
                instances: Instances<ParametrisedSpec>
        ) = Application(
                operands.map { it.instantiate(typeParams, instances) },
                instancePath,
                type.instantiate(typeParams)
        )

        override fun toString() = "(${operands.joinToString(" ")})"
    }

    data class LetAbstraction(
            val expression: ParametrisedSpec,
            val bindings: List<LambdaName>,
            override val instancePath: InstanceSignature,
            override val type: Parametrised<Type>
    ) : ParametrisedSpec() {
        override fun instantiate(
                typeParams: Map<TypeVariableName, Type>,
                instances: Instances<ParametrisedSpec>
        ) = LetAbstraction(
                expression.instantiate(typeParams, instances),
                bindings,
                instancePath,
                type.instantiate(typeParams)
        )

        override fun toString() = "(@let ... $expression)"
    }
}