package thesis.preprocess.spec

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.Instances
import thesis.preprocess.results.ParametrisedSpecs
import thesis.preprocess.results.Specs
import thesis.preprocess.spec.parametrised.ParametrisedSpec
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
        val typeSpecs = TypeSpecCompiler().process(data.typeInstances)

        data.instances.forEach { signature, type, spec ->
            if (signature.size > 1) {
                // Will be instantiated later
                return@forEach
            }
            if (spec.isInstantiated()) {
                val instantiated = spec.instantiate(
                        emptyMap(),
                        instances,
                        data.instances,
                        typeSpecs,
                        DataPointer(0, 0)
                )
                instances[signature, type] = instantiated
            } else {
                parametrisedInstances[signature, type] = spec
            }
        }

        return Specs(
                typeSpecs,
                instances,
                parametrisedInstances,
                data.trainable
        )
    }

    private fun ParametrisedSpec.instantiate(
            variables: Map<LambdaName, Spec.Variable>,
            instances: Instances<Spec>,
            parametrisedInstances: Instances<ParametrisedSpec>,
            typeSpecs: Instances<TypeSpec>,
            dataPointer: DataPointer
    ): Spec {
        val typeInstance = type.type   // Assuming that its' fully instantiated
        return when (this) {
            is ParametrisedSpec.Variable -> variables[name] ?: throw UnknownExpressionError(name)
            is ParametrisedSpec.Object -> {
                val algebraic = (typeInstance as Type.Application).type
                val spec = typeSpecs[algebraic.signature, type]
                        ?: throw UnknownTypeError(algebraic.name)
                val info = spec.constructors[name] ?: throw UnknownExpressionError(name)
                Spec.Object(
                        typeInstance,
                        info.toType.size,
                        info.start
                )
            }
            is ParametrisedSpec.Function.Constructor -> {
                val algebraic = ((typeInstance as Type.Function).getResultType() as Type.Application).type
                val spec = typeSpecs[algebraic.signature, type]
                        ?: throw UnknownTypeError(algebraic.name)
                val info = spec.constructors[name] ?: throw UnknownExpressionError(name)
                Spec.Function.Constructor(
                        name,
                        typeInstance,
                        info.arguments.map { it.structure.size }.sum(),
                        info.toType.size,
                        info.start
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
                            instance.structure.size
                        }
                    }
                }

                Spec.Function.Trainable(
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
                            val start = thisOffset
                            val spec = typeSpecs[argType.type.signature, parametrised]
                                    ?: throw UnknownTypeError(argType.type.name)
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
                        argument to Spec.Variable.Function(argument, typeInstance, functionsCount++)
                )
                Spec.Function.Recursive(
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
                return Spec.Variable.External(
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

                Spec.Application(
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
                    var functionsCount = 0
                    var thisOffset = 0

                    val variablesMap = mutableMapOf<LambdaName, Spec.Variable>()
                    val dataPatterns = case.patterns.flatMap { pattern ->
                        val patternType = pattern.type.type
                        when (patternType) {
                            is Type.Variable -> throw IllegalStateException(
                                    "Type variable ${pattern.type} should be instantiated"
                            )
                            is Type.Application -> {
                                // All types should be instantiated at this step
                                val argType = patternType.type
                                val spec = typeSpecs[
                                        argType.signature,
                                        patternType.args.map { it.toRaw() }
                                ]
                                        ?: throw UnknownTypeError(argType.name)
                                val start = thisOffset
                                thisOffset += spec.structure.size

                                pattern.getPatterns(
                                        variablesMap,
                                        start to thisOffset,
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
                                        typeInstance,
                                        position
                                )
                                emptyList()
                            }
                        }
                    }
                    val compiledCase = case.body.instantiate(
                            variables + variablesMap,
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
                        typeInstance,
                        cases
                )
            }
        }
    }

    private fun ParametrizedPattern.getPatterns(
            variables: MutableMap<LambdaName, Spec.Variable>,
            positions: Pair<Int, Int>,
            typeSpecs: Instances<TypeSpec>
    ): List<DataPattern> {
        // At this step every type is instantiated, so it can't be variable or function
        val thisType = type.type as Type.Application
        val signature = thisType.type.signature
        val typeSignature = thisType.args.map { it.toRaw() }
        val spec = typeSpecs[signature, typeSignature] ?: throw UnknownTypeError(thisType.type.name)
        return when (this) {
            is TypedPattern.Object -> {
                val constructor = spec.constructors[name] ?: throw UnknownExpressionError(name)
                val pattern = DataPattern.Object(name, positions.first + constructor.start)
                listOf(pattern)
            }
            is TypedPattern.Variable -> {
                val variable = Spec.Variable.Object(this.type.type, positions)
                variables[name] = variable
                val pattern = DataPattern.Variable(
                        name,
                        spec.name,
                        spec,
                        signature,
                        typeSignature,
                        positions.first,
                        positions.second
                )
                listOf(pattern)
            }
            is TypedPattern.Constructor -> {
                val constructor = spec.constructors[name] ?: throw UnknownExpressionError(name)

                var offset = positions.first
                arguments.zip(constructor.arguments) { argument, typeSpec ->
                    val start = offset
                    offset += typeSpec.structure.size
                    argument.getPatterns(
                            variables,
                            start to offset,
                            typeSpecs
                    )
                }.flatMap { it }
            }
        }
    }
}