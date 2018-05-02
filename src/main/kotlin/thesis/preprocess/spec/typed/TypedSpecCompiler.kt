package thesis.preprocess.spec.typed

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceName
import thesis.preprocess.results.Instances
import thesis.preprocess.results.ParametrisedSpecs
import thesis.preprocess.results.TypedSpecs
import thesis.preprocess.spec.DataPattern
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.ParametrizedPattern
import thesis.preprocess.spec.parametrised.AlgebraicTypeInstance
import thesis.preprocess.spec.parametrised.ParametrisedSpec
import thesis.preprocess.types.UnknownExpressionError
import thesis.preprocess.types.UnknownTypeError
import thesis.preprocess.types.UnsupportedTrainableType

/**
 * Compiles parametrised specs to usual specs
 *
 * @author Danil Kolikov
 */
class TypedSpecCompiler : Processor<ParametrisedSpecs, TypedSpecs> {

    override fun process(data: ParametrisedSpecs): TypedSpecs {
        val polymorphicExpressions = Instances<TypedSpec>()
        val expressionInstances = Instances<ExpressionInstance>()

        val parametrisedInstances = Instances<ParametrisedSpec>()

        data.instances.forEach { signature, type, spec ->
            if (signature.size > 1) {
                // Will be instantiated later
                return@forEach
            }
            if (spec.isInstantiated()) {
                val instantiated = spec.instantiate(
                        emptyMap(),
                        polymorphicExpressions,
                        data.instances,
                        data.typeInstances,
                        DataPointer(0, 0)
                )
                polymorphicExpressions[signature, type] = instantiated
            } else {
                parametrisedInstances[signature, type] = spec
            }
        }

        return TypedSpecs(
                data.typeDefinitions,
                data.typeInstances,
                polymorphicExpressions,
                expressionInstances,
                data.trainable
        )
    }

    private fun ParametrisedSpec.instantiate(
            variables: Map<LambdaName, TypedSpec.Variable>,
            instances: Instances<TypedSpec>,
            parametrisedInstances: Instances<ParametrisedSpec>,
            typeSpecs: Instances<AlgebraicTypeInstance>,
            dataPointer: DataPointer
    ): TypedSpec {
        val typeInstance = type.type   // Assuming that it's fully instantiated
        return when (this) {
            is ParametrisedSpec.Variable -> variables[name] ?: throw UnknownExpressionError(name)
            is ParametrisedSpec.Object -> {
                val type = typeInstance.getResultType() as Type.Application
                val typeName = InstanceName(type.signature, type.typeSignature)
                TypedSpec.Object(
                        typeInstance,
                        typeName,
                        this.position
                )
            }
            is ParametrisedSpec.Function.Constructor -> {
                val type = typeInstance.getResultType() as Type.Application
                val typeName = InstanceName(type.signature, type.typeSignature)
                TypedSpec.Function.Constructor(
                        name,
                        typeInstance,
                        typeName,
                        this.position
                )
            }
            is ParametrisedSpec.Function.Trainable -> {
                if (typeInstance.getOperands().size != trainableSpec.type.getOperands().size) {
                    // Extra parameters appeared - it was instantiated by function type
                    throw UnsupportedTrainableType(typeInstance)
                }
                val sizes = type.typeParams.mapValues { (_, v) ->
                    when (v) {
                        is Type.Variable -> throw IllegalStateException("Type should be instantiated")
                        is Type.Function -> throw IllegalStateException("Instantiation by functions is unsupported")
                        is Type.Application -> {
                            val typeSignature = v.args.map { it.toRaw() }
                            val instance = typeSpecs[v.type.signature, typeSignature] ?: throw IllegalStateException(
                                    "Type ${v.type.name} with arguments $typeSignature " +
                                            "is not instantiated"
                            )
                            TODO("Add support for trainables")
                        }
                    }
                }

                TypedSpec.Function.Trainable(
                        instanceSignature,
                        instancePosition,
                        trainableSpec,
                        typeInstance,
                        dataPointer,
                        sizes
                )
            }
            is ParametrisedSpec.Function.Anonymous -> {
                var thisOffset = dataPointer.dataOffset
                var functionsCounter = dataPointer.functionsCount
                val newVariables = this.arguments.map { (name, parametrised) ->
                    val argType = parametrised.type
                    when (argType) {
                        is Type.Variable -> throw IllegalStateException("Unexpected variable ${argType.name}")
                        is Type.Application -> {
                            // It is object now, as we don't support mixed data types
                            name to TypedSpec.Variable.Object(argType, thisOffset++)
                        }
                        is Type.Function -> {
                            // Some function - save position in input
                            name to TypedSpec.Variable.Function(name, argType, functionsCounter++)
                        }
                    }
                }.toMap()

                TypedSpec.Function.Anonymous(
                        typeInstance,
                        body.instantiate(
                                variables + newVariables,
                                instances,
                                parametrisedInstances,
                                typeSpecs,
                                DataPointer(thisOffset, functionsCounter)
                        ),
                        dataPointer
                )
            }
            is ParametrisedSpec.Function.Recursive -> {
                var functionsCount = dataPointer.functionsCount
                val newVariables = variables + mapOf(
                        argument to TypedSpec.Variable.Function(argument, typeInstance, functionsCount++)
                )
                TypedSpec.Function.Recursive(
                        argument,
                        body.instantiate(
                                newVariables,
                                instances,
                                parametrisedInstances,
                                typeSpecs,
                                DataPointer(dataPointer.dataOffset, functionsCount)
                        ),
                        typeInstance,
                        dataPointer
                )
            }
            is ParametrisedSpec.LetAbstraction -> {
                bindings.forEach { name ->
                    val signature = instancePath + listOf(name)
                    parametrisedInstances.getInstances(signature).forEach { type, instance ->
                        if (instance.isInstantiated()) {
                            val instantiated = instance.instantiate(
                                    variables,
                                    instances,
                                    parametrisedInstances,
                                    typeSpecs,
                                    dataPointer
                            )
                            instances[signature, type] = instantiated
                        }
                    }
                }
                expression.instantiate(variables, instances, parametrisedInstances, typeSpecs, dataPointer)
            }
            is ParametrisedSpec.Function.Polymorphic -> {
                val instance = instances[signature, typeSignature] ?: throw IllegalStateException(
                        "Expression with signature $signature $typeSignature wasn't instantiated"
                )
                return TypedSpec.Variable.External(
                        signature,
                        typeSignature,
                        typeInstance,
                        instance
                )
            }
            is ParametrisedSpec.Application -> {
                val operands = operands.map {
                    it.instantiate(variables, instances, parametrisedInstances, typeSpecs, dataPointer)
                }

                // Resolve variables and eval applications
                val call = operands.mapIndexed { index, rawLambda ->
                    when (rawLambda) {
                        is TypedSpec.Variable -> index
                        is TypedSpec.Application -> index
                        else -> null
                    }
                }.filterNotNull()

                // Save positions of constants to evaluate them
                val constants = operands.mapIndexed { index, rawLambda ->
                    when (rawLambda) {
                        is TypedSpec.Variable.External -> if (rawLambda.type.isObject)
                            index else null
                        else -> null
                    }
                }.filterNotNull()

                // Functions without arguments are data
                val data = operands.mapIndexed { index, lambda ->
                    if (lambda.type is Type.Application) index else null
                }.filterNotNull()

                // Functions with arguments are functions
                val functions = operands.mapIndexed { index, lambda ->
                    if (lambda.type is Type.Function) index else null
                }.filterNotNull()

                // Instance shouldn't contain type variables
                if (operands.any { it.type is Type.Variable }) {
                    throw IllegalStateException("Expression $this contains type variables")
                }

                TypedSpec.Application(
                        typeInstance,
                        operands,
                        call,
                        constants,
                        data,
                        functions
                )
            }
            is ParametrisedSpec.Function.Guarded -> {
                val cases = this.cases.map { case ->
                    // Fake tuple instance for simplified compilation of a pattern
                    val tuple = TypedPattern.Constructor(
                            TUPLE_TYPE_NAME,
                            case.patterns,
                            Parametrised(emptyList(), Type.Variable(TUPLE_TYPE_NAME), emptyMap())
                    )
                    val variablesMap = mutableMapOf<LambdaName, TypedSpec.Variable>()
                    val (pattern, nextPointer) = tuple.toTypedPattern(variablesMap, dataPointer)
                    val compiledCase = case.body.instantiate(
                            variables + variablesMap,
                            instances,
                            parametrisedInstances,
                            typeSpecs,
                            nextPointer
                    )
                    TypedSpec.Function.Guarded.Case(
                            pattern!!,
                            compiledCase
                    )
                }
                val resultType = type.type.getResultType() as Type.Application
                TypedSpec.Function.Guarded(
                        name,
                        typeInstance,
                        InstanceName(resultType.signature, resultType.typeSignature),
                        cases
                )
            }
        }
    }

    private fun ParametrizedPattern.toTypedPattern(
            variables: MutableMap<LambdaName, TypedSpec.Variable>,
            pointer: DataPointer
    ): Pair<PatternInstance?, DataPointer> {
        return when (this) {
            is TypedPattern.Variable -> {
                if (type.type is Type.Function) {
                    variables[name] = TypedSpec.Variable.Function(
                            name,
                            type.type,
                            pointer.functionsCount
                    )
                    null to DataPointer(pointer.dataOffset, pointer.functionsCount + 1)
                } else {
                    variables[name] = TypedSpec.Variable.Object(
                            type.type,
                            pointer.dataOffset
                    )
                    PatternInstance.Variable() to DataPointer(pointer.dataOffset + 1, pointer.functionsCount)
                }
            }
            is TypedPattern.Object -> {
                val objectType = type.type as Type.Application
                val constructor = objectType.type.constructors[name] ?: throw UnknownExpressionError(name)
                PatternInstance.Literal(constructor.position) to pointer
            }
            is TypedPattern.Constructor -> {
                val position = if (name == TUPLE_TYPE_NAME) 0 else {
                    val resultType = type.type.getResultType() as Type.Application
                    val constructor = resultType.type.constructors[name] ?: throw UnknownExpressionError(name)
                    constructor.position
                }
                var curPointer = pointer
                val arguments = mutableListOf<PatternInstance>()
                this.arguments.forEach {
                    val (argument, nextPointer) = it.toTypedPattern(variables, curPointer)
                    argument?.let {
                        arguments.add(it)
                    }
                    curPointer = nextPointer
                }
                PatternInstance.Constructor(
                        position,
                        arguments
                ) to curPointer
            }
        }
    }

    companion object {
        const val TUPLE_TYPE_NAME = "..tuple"
    }
}