package thesis.preprocess.spec

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.Instances
import thesis.preprocess.results.ParametrisedSpecs
import thesis.preprocess.results.Specs
import thesis.preprocess.types.UnknownExpressionError
import thesis.preprocess.types.UnknownTypeError
import thesis.preprocess.types.UnsupportedTrainableType

/**
 * Specifies parameters for parametrised specs
 *
 * @author Danil Kolikov
 */
class SpecCompiler : Processor<ParametrisedSpecs, Specs> {

    override fun process(data: ParametrisedSpecs): Specs {
        val instances = Instances<Spec>()
        val parametrisedInstances = Instances<ParametrisedSpec>()

        data.instances.forEach { signature, type, spec ->
            if (signature.size > 1) {
                return@forEach "Will be instantiated later"
            }
            if (spec.isInstantiated()) {
                val instantiated = spec.instantiate(
                        emptyMap(),
                        instances,
                        data.instances,
                        data.typeSpecs,
                        DataPointer(0, 0)
                )
                instances.set(signature, type, instantiated)
            } else {
                parametrisedInstances.set(signature, type, spec)
            }
        }

        return Specs(
                data.typeSpecs,
                instances,
                parametrisedInstances,
                data.trainable
        )
    }

    private fun ParametrisedSpec.instantiate(
            variables: Map<LambdaName, Spec.Variable>,
            instances: Instances<Spec>,
            parametrisedInstances: Instances<ParametrisedSpec>,
            typeSpecs: Map<TypeName, TypeSpec>,
            dataPointer: DataPointer
    ): Spec {
        val type = this.type.type   // Assuming that its' fully instantiated
        return when (this) {
            is ParametrisedSpec.Variable -> variables[name] ?: throw UnknownExpressionError(name)
            is ParametrisedSpec.Object -> Spec.Object(
                    type,
                    info.toType.size,
                    info.start
            )
            is ParametrisedSpec.Function.Constructor -> Spec.Function.Constructor(
                    name,
                    type,
                    info.arguments.map { it.structure.size }.sum(),
                    info.toType.size,
                    info.start
            )
            is ParametrisedSpec.Function.Trainable -> {
                if (type.getArguments().size != trainableSpec.argumentTypes.size) {
                    // Extra parameters appeared - it was instantiated by function type
                    throw UnsupportedTrainableType(type)
                }
                val instantiateBy = mutableListOf<TypeName>()
                trainableSpec.argumentTypes.zip(type.getArguments()).forEach { (type, arg) ->
                    if (arg is Type.Algebraic) {
                        if (type == null) {
                            // Variable was instantiated
                            instantiateBy.add(arg.type.name)
                        }
                    } else {
                        throw UnsupportedTrainableType(arg)
                    }
                }
                val resultType = type.getResultType() as? Type.Algebraic
                        ?: throw UnsupportedTrainableType(type.getResultType())
                val resultTypeName = if (trainableSpec.resultType == null) resultType.type.name else null

                Spec.Function.Trainable(
                        instanceSignature,
                        instancePosition,
                        trainableSpec,
                        type,
                        dataPointer,
                        instantiateBy,
                        resultTypeName
                )
            }
            is ParametrisedSpec.Function.Anonymous -> {
                var thisOffset = dataPointer.dataOffset
                var functionsCounter = dataPointer.functionsCount
                val newVariables = this.arguments.map { (name, parametrised) ->
                    val argType = parametrised.type
                    when (argType) {
                        is Type.Variable -> throw IllegalStateException("Unexpected variable $name")
                        is Type.Algebraic -> {
                            // Object - save positions of representation in array
                            val start = thisOffset
                            val spec = typeSpecs[argType.type.name] ?: throw UnknownTypeError(argType.type.name)
                            thisOffset += spec.structure.size
                            val positions = start to thisOffset
                            name to Spec.Variable.Object(
                                    argType,
                                    positions
                            )
                        }
                        is Type.Function -> {
                            // Some function - save position in input
                            name to Spec.Variable.Function(name, argType, functionsCounter++)
                        }
                    }
                }.toMap()

                Spec.Function.Anonymous(
                        type,
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
                            instances.set(signature, type, instantiated)
                        }
                    }
                }
                expression.instantiate(variables, instances, parametrisedInstances, typeSpecs, dataPointer)
            }
            is ParametrisedSpec.Function.Polymorphic -> {
                val instance = instances.get(signature, typeSignature) ?: throw IllegalStateException(
                        "Expression with signature $signature $typeSignature wasn't instantiated"
                )
                return Spec.Variable.External(
                        signature,
                        typeSignature,
                        type,
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
                        is Spec.Variable -> index
                        is Spec.Application -> index
                        else -> null
                    }
                }.filterNotNull()

                // Save positions of constants to evaluate them
                val constants = operands.mapIndexed { index, rawLambda ->
                    when (rawLambda) {
                        is Spec.Object -> index
                        else -> null
                    }
                }.filterNotNull()

                // Functions without arguments are data
                val data = operands.mapIndexed { index, lambda ->
                    if (lambda.type is Type.Algebraic) index else null
                }.filterNotNull()

                // Functions with arguments are functions
                val functions = operands.mapIndexed { index, lambda ->
                    if (lambda.type is Type.Function) index else null
                }.filterNotNull()

                // Instance shouldn't contain type variables
                if (operands.any { it.type is Type.Variable }) {
                    throw IllegalStateException("Expression $this contains type variables")
                }

                Spec.Application(
                        type,
                        operands,
                        call,
                        constants,
                        data,
                        functions
                )
            }
            is ParametrisedSpec.Function.Guarded -> {
                val cases = this.cases.map { case ->
                    var functionsCount = 0
                    var thisOffset = 0
                    val dataPatterns = mutableListOf<DataPattern>()

                    val variablesMap = mutableMapOf<LambdaName, Spec.Variable>()
                    case.patterns.map { pattern ->
                        when (pattern.type.type) {
                            is Type.Algebraic -> {
                                val name = (pattern.type.type as Type.Algebraic).type.name
                                val spec = typeSpecs[name] ?: throw UnknownTypeError(name)
                                val start = thisOffset
                                thisOffset += spec.structure.size

                                pattern.addVariables(
                                        variablesMap,
                                        dataPatterns,
                                        start to thisOffset,
                                        name,
                                        typeSpecs
                                )
                            }
                            is Type.Function -> {
                                // When argument is function, pattern is variable
                                // (Because we can't pattern-match by function)
                                val functionName = (pattern as TypedPattern.Variable).name

                                val position = functionsCount++
                                variablesMap[functionName] = Spec.Variable.Function(
                                        functionName,
                                        type,
                                        position
                                )
                            }
                        }
                    }
                    val compiledCase = case.body.instantiate(
                            variablesMap,
                            instances,
                            parametrisedInstances,
                            typeSpecs,
                            DataPointer(thisOffset, functionsCount)
                    )
                    Spec.Function.Guarded.Case(
                            dataPatterns,
                            compiledCase
                    )
                }
                Spec.Function.Guarded(
                        name,
                        type,
                        cases
                )
            }
        }
    }

    private fun ParametrizedPattern.addVariables(
            variables: MutableMap<LambdaName, Spec.Variable>,
            patterns: MutableList<DataPattern>,
            positions: Pair<Int, Int>,
            typeName: TypeName,
            typeSpecs: Map<TypeName, TypeSpec>
    ) {
        when (this) {
            is TypedPattern.Object -> {
                val spec = typeSpecs[typeName] ?: throw UnknownTypeError(typeName)
                val constructor = spec.constructors[name] ?: throw UnknownExpressionError(name)
                patterns.add(
                        DataPattern.Object(name, positions.first + constructor.start)
                )
            }
            is TypedPattern.Variable -> {
                val variable = Spec.Variable.Object(type.type, positions)
                variables[name] = variable
                patterns.add(
                        DataPattern.Variable(
                                name,
                                typeName,
                                typeSpecs[typeName] ?: throw UnknownTypeError(typeName),
                                positions.first,
                                positions.second
                        )
                )
            }
            is TypedPattern.Constructor -> {
                val spec = typeSpecs[typeName] ?: throw UnknownTypeError(typeName)
                val constructor = spec.constructors[name] ?: throw UnknownExpressionError(name)

                var offset = positions.first
                arguments.zip(constructor.arguments) { argument, typeSpec ->
                    val start = offset
                    offset += typeSpec.structure.size
                    argument.addVariables(
                            variables,
                            patterns,
                            start to offset,
                            typeSpec.name,
                            typeSpecs
                    )
                }
            }
        }
    }
}