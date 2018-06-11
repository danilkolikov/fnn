package thesis.preprocess.spec.typed

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.*
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.ParametrizedPattern
import thesis.preprocess.spec.parametrised.ParametrisedSpec
import thesis.preprocess.types.UnknownExpressionError

/**
 * Compiles parametrised specs to usual specs
 *
 * @author Danil Kolikov
 */
class TypedSpecCompiler : Processor<ParametrisedSpecs, TypedSpecs> {

    override fun process(data: ParametrisedSpecs): TypedSpecs {
        val expressionInstances = LinkedHashMap<InstanceSignature, TypedSpec>()

        data.expressions.forEach { signature, spec ->
            if (!expressionInstances.containsKey(signature)) {
                expressionInstances[signature] = spec.instantiate(
                        emptyMap(),
                        expressionInstances,
                        data.types,
                        DataPointer(0, 0)
                )
            }
        }

        return TypedSpecs(
                data.types,
                expressionInstances
        )
    }

    private fun ParametrisedSpec.instantiate(
            variables: Map<LambdaName, TypedSpec.Variable>,
            instances: LinkedHashMap<InstanceSignature, TypedSpec>,
            typeSpecs: LinkedHashMap<TypeName, AlgebraicType>,
            dataPointer: DataPointer
    ): TypedSpec {
        val typeInstance = type.type   // Assuming that it's fully instantiated
        return when (this) {
            is ParametrisedSpec.Variable -> variables[name] ?: throw UnknownExpressionError(name)
            is ParametrisedSpec.Object -> {
                val type = typeInstance.getResultType() as Type.Application
                TypedSpec.Object(
                        typeInstance,
                        type,
                        this.position
                )
            }
            is ParametrisedSpec.Function.Constructor -> {
                val type = typeInstance.getResultType() as Type.Application
                TypedSpec.Function.Constructor(
                        name,
                        typeInstance,
                        type,
                        this.position
                )
            }
            is ParametrisedSpec.Function.Trainable -> {
                TypedSpec.Function.Trainable(
                        instanceSignature,
                        trainableSpec,
                        typeInstance,
                        dataPointer
                )
            }
            is ParametrisedSpec.Function.Anonymous -> {
                var thisOffset = dataPointer.dataOffset
                var functionsCounter = dataPointer.functionsCount
                val newVariables = this.arguments.map { (name, parametrised) ->
                    val argType = parametrised.type
                    when (argType) {
                        is Type.Variable -> {
                            // It is object now, as we don't support mixed data types
                            name to TypedSpec.Variable.Object(argType, thisOffset++)
                        }
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
                                typeSpecs,
                                DataPointer(dataPointer.dataOffset, functionsCount)
                        ),
                        isTailRecursive,
                        typeInstance,
                        dataPointer
                )
            }
            is ParametrisedSpec.LetAbstraction -> expression.instantiate(variables, instances, typeSpecs, dataPointer)
            is ParametrisedSpec.Function.Polymorphic -> {
                return TypedSpec.Variable.External(
                        signature,
                        typeInstance
                )
            }
            is ParametrisedSpec.Application -> {
                val operands = operands.map {
                    it.instantiate(variables, instances, typeSpecs, dataPointer)
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

                // Applications or Variables are data, Functions not
                val data = operands.mapIndexed { index, lambda ->
                    if (lambda.type !is Type.Function) index else null
                }.filterNotNull()

                // Find functions
                val functions = operands.mapIndexed { index, lambda ->
                    if (lambda.type is Type.Function) index else null
                }.filterNotNull()

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
                            typeSpecs,
                            nextPointer
                    )
                    TypedSpec.Function.Guarded.Case(
                            pattern!!,
                            compiledCase
                    )
                }
                TypedSpec.Function.Guarded(
                        typeInstance,
                        cases,
                        dataPointer
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