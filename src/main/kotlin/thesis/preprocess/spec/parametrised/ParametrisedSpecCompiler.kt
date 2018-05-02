package thesis.preprocess.spec.parametrised

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.typeSignature
import thesis.preprocess.results.*
import thesis.preprocess.types.UnknownExpressionError

/**
 * Compiles Lambda expressions to specs with parameters
 *
 * @author Danil Kolikov
 */
class ParametrisedSpecCompiler : Processor<InferredExpressions, ParametrisedSpecs> {

    override fun process(data: InferredExpressions): ParametrisedSpecs {
        val compiled = mutableMapOf<LambdaName, ParametrisedSpec>()
        val instances = Instances<ParametrisedSpec>()
        val typeInstances = Instances<AlgebraicTypeInstance>()
        val trainable = LinkedHashMap<InstanceSignature, MutableList<ParametrisedTrainableSpec>>()

        // Add constructors
        val definedTypes = mutableSetOf<TypeName>()
        data.typeDefinitions.forEach { _, type ->
            type.constructors.forEach { name, constructor ->
                val parametrised = constructor.type
                val spec = when (parametrised.type) {
                    is Type.Variable -> throw IllegalStateException(
                            "Variable $name should have been instantiated"
                    )
                    is Type.Application -> {
                        ParametrisedSpec.Object(
                                name,
                                type,
                                constructor.position,
                                parametrised
                        )
                    }
                    is Type.Function -> ParametrisedSpec.Function.Constructor(
                            name,
                            type,
                            constructor.position,
                            parametrised
                    )
                }
                instances.putIfAbsent(spec.instancePath, listOf(), spec)
                compiled[name] = spec
            }
            definedTypes.add(type.name)
        }

        // Add expressions
        data.lambdaDefinitions.forEach { name, lambda ->
            val cases = mutableListOf<ParametrisedSpec.Function.Guarded.Case>()

            val variables = if (lambda.isRecursive) mapOf(
                    name to ParametrisedSpec.Variable(
                            name,
                            emptyList(),
                            lambda.type
                    )
            ) else emptyMap()
            lambda.expressions.forEach { (patterns, lambda) ->

                val newVariables = variables + patterns.flatMap { it.getTypedVariables().toList() }
                        .map {
                            it.first to ParametrisedSpec.Variable(
                                    it.first,
                                    listOf(name),
                                    it.second
                            )
                        }
                        .toMap()

                val case = lambda.compile(
                        newVariables,
                        compiled,
                        listOf(name),
                        instances,
                        trainable
                )
                val application = if (case is ParametrisedSpec.Function.Polymorphic) {
                    // It's a call of functions, it's better to wrap into Application
                    ParametrisedSpec.Application(
                            listOf(case),
                            listOf(name),
                            case.type
                    )
                } else case

                cases.add(ParametrisedSpec.Function.Guarded.Case(
                        patterns,
                        application
                ))
            }
            val guarded = ParametrisedSpec.Function.Guarded(name, cases, lambda.type)
            val result = if (lambda.isRecursive) ParametrisedSpec.Function.Recursive(
                    name,
                    guarded,
                    emptyList(),
                    lambda.type
            ) else guarded
            instances.putIfAbsent(guarded.instancePath, emptyList(), result)
            compiled[name] = guarded
        }

        // Add type instances
        instances.forEach { _, _, spec ->
            if (!spec.isInstantiated()) {
                return@forEach
            }
            // Extract types only from instantiated specs
            spec.type.modifyType {
                it.extractTypeInstances(typeInstances)
                it
            }
        }
        return ParametrisedSpecs(
                data.typeDefinitions,
                typeInstances,
                instances,
                trainable.mapValuesTo(LinkedHashMap()) { (_, v) -> v.toList() }
        )
    }

    private fun TypedLambda<Type>.compile(
            variables: Map<LambdaName, ParametrisedSpec.Variable>,
            compiled: Map<LambdaName, ParametrisedSpec>,
            instancePath: List<LambdaName>,
            instances: Instances<ParametrisedSpec>,
            trainable: LinkedHashMap<InstanceSignature, MutableList<ParametrisedTrainableSpec>>
    ): ParametrisedSpec = when (this) {
        is TypedLambda.Trainable -> {
            trainable.computeIfAbsent(instancePath, { mutableListOf() })
            val trainableSpecs = trainable[instancePath]!!
            val spec = ParametrisedTrainableSpec.fromType(type)
            trainableSpecs.add(spec)
            ParametrisedSpec.Function.Trainable(
                    instancePath,
                    trainableSpecs.size - 1,
                    spec,
                    instancePath,
                    type
            )
        }
        is TypedLambda.Literal -> {
            val defined = compiled[name]?.let {
                if (type.initialParameters.isEmpty()) {
                    // Type has no type params, no need to instantiate
                    instances.putIfAbsent(it.instancePath, emptyList(), it)
                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            it,
                            emptyMap(),
                            it.instancePath,
                            emptyList(),
                            instancePath,
                            it.type
                    )
                } else {
                    // Should instantiate
                    val typeParams = type.typeParams
                    val signature = it.instancePath
                    val typeSignature = type.typeSignature()
                    val instance = instances[signature, typeSignature] ?: it.instantiate(
                            typeParams,
                            instances
                    )
                    instances.putIfAbsent(signature, typeSignature, instance)

                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            instance,
                            typeParams,
                            signature,
                            typeSignature,
                            instancePath,
                            instance.type
                    )
                }
            }
            val variable = variables[name]
            defined ?: variable ?: throw UnknownExpressionError(name)
        }
        is TypedLambda.Abstraction -> {
            val args = arguments.map { it.name to it.type }.toMap(LinkedHashMap())
            val vars = args.entries.map { (name, type) ->
                name to ParametrisedSpec.Variable(
                        name,
                        instancePath,
                        type
                )
            }.toMap()
            ParametrisedSpec.Function.Anonymous(
                    args,
                    expression.compile(
                            variables + vars,
                            compiled,
                            instancePath,
                            instances,
                            trainable
                    ),
                    instancePath,
                    type
            )
        }
        is TypedLambda.LetAbstraction -> {
            val bound = mutableListOf<LambdaName>()
            val newCompiled = HashMap<LambdaName, ParametrisedSpec>(compiled)
            bindings.forEach {
                val name = it.name
                val binding = it.expression.compile(
                        variables,
                        newCompiled,
                        instancePath + listOf(name),
                        instances,
                        trainable
                )
                newCompiled[it.name] = binding
                bound.add(it.name)
            }
            val compiledExpr = expression
                    .compile(variables, newCompiled, instancePath, instances, trainable)
            ParametrisedSpec.LetAbstraction(
                    compiledExpr,
                    bound,
                    instancePath,
                    compiledExpr.type
            )
        }
        is TypedLambda.RecAbstraction -> {
            val vars = variables + mapOf(argument.name to ParametrisedSpec.Variable(
                    argument.name,
                    instancePath,
                    type
            ))
            ParametrisedSpec.Function.Recursive(
                    argument.name,
                    expression.compile(
                            vars, compiled, instancePath, instances, trainable
                    ),
                    instancePath,
                    type
            )
        }
        is TypedLambda.Application -> {
            val operands = (listOf(function) + this.arguments).map {
                it.compile(variables, compiled, instancePath, instances, trainable)
            }
            ParametrisedSpec.Application(
                    operands,
                    instancePath,
                    type
            )
        }
    }

    private fun Type.extractTypeInstances(
            instances: Instances<AlgebraicTypeInstance>
    ): InstanceName? = when (this) {
        is Type.Variable -> throw IllegalStateException(
                "All type variables should be instantiated"
        )
        is Type.Function -> {
            from.extractTypeInstances(instances)
            to.extractTypeInstances(instances)
            null
        }
        is Type.Application -> {
            val signature = this.signature
            val typeSignature = this.typeSignature
            val name = InstanceName(signature, typeSignature)
            if (instances.containsKey(signature, typeSignature)) {
                // We already have such type
                name
            } else {
                // We can't have Functions in ADTs, so all operands are Applications
                // Therefore all names will be not null
                val names = args.map { it.extractTypeInstances(instances) }.map { it!! }
                val typeParams = type.parameters.zip(args).toMap()
                instances[signature, typeSignature] = AlgebraicTypeInstance(
                        type.instantiate(typeParams),
                        name,
                        type.parameters.zip(names).toMap()
                )
                name
            }
        }
    }
}