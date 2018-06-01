package thesis.preprocess.spec.parametrised

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
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
        val instances = Instances<Polymorphic<ParametrisedSpec>>()

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
                val polymorphic = Polymorphic.Base(
                        spec,
                        InstanceName(listOf(name), type.typeSignature)
                )
                instances.putIfAbsent(polymorphic.name, polymorphic)
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
                        instances
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
                    listOf(name),
                    lambda.type
            ) else guarded
            val polymorphic = Polymorphic.Base<ParametrisedSpec>(
                    result,
                    InstanceName(guarded.instancePath, guarded.type.typeSignature())
            )
            instances.putIfAbsent(polymorphic.name, polymorphic)
            compiled[name] = result
        }

        // Add type instances
        val typeInstances = Instances<Polymorphic<AlgebraicType>>()
        instances.forEach { _, _, spec ->
            spec.item.type.modifyType {
                it.extractTypeInstances(typeInstances)
                it
            }
        }
        return ParametrisedSpecs(
                typeInstances,
                instances
        )
    }

    private fun TypedLambda<Type>.compile(
            variables: Map<LambdaName, ParametrisedSpec.Variable>,
            compiled: Map<LambdaName, ParametrisedSpec>,
            instancePath: List<LambdaName>,
            instances: Instances<Polymorphic<ParametrisedSpec>>
    ): ParametrisedSpec = when (this) {
        is TypedLambda.Trainable -> {
            val arguments = type.type.getArguments().map { it.toSignature() }
            val toType = type.type.getResultType().toSignature()
            val spec = ParametrisedTrainableSpec(
                    options,
                    arguments,
                    toType
            )
            ParametrisedSpec.Function.Trainable(
                    instancePath,
                    spec,
                    instancePath,
                    type
            )
        }
        is TypedLambda.Literal -> {
            val defined = compiled[name]?.let {
                if (type.initialParameters.isEmpty()) {
                    // Type has no type params, no need to instantiate
                    val polymorphic = Polymorphic.Base(
                            it,
                            InstanceName(listOf(name), emptyList())
                    )
                    instances.putIfAbsent(polymorphic.name, polymorphic)
                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            polymorphic,
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

                    val baseName = InstanceName(signature, type.initialParameters.map { TypeSig.Variable(it) })
                    var base = instances[baseName]
                    if (base == null) {
                        // Should save instance
                        base = Polymorphic.Base(
                                it,
                                baseName
                        )
                        instances[baseName] = base
                    }
                    val instance = instances[signature, typeSignature] ?: Polymorphic.Instance(
                            it.instantiate(
                                    typeParams,
                                    instances
                            ),
                            base,
                            InstanceName(signature, typeSignature),
                            typeParams.mapValues { (_, v) -> v.toSignature() }
                    )
                    instances.putIfAbsent(signature, typeSignature, instance)

                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            instance,
                            typeParams,
                            signature,
                            typeSignature,
                            instancePath,
                            instance.item.type
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
                            instances
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
                        instances
                )
                newCompiled[it.name] = binding
                bound.add(it.name)
            }
            val compiledExpr = expression
                    .compile(variables, newCompiled, instancePath, instances)
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
                            vars, compiled, instancePath, instances
                    ),
                    instancePath,
                    type
            )
        }
        is TypedLambda.Application -> {
            val operands = (listOf(function) + this.arguments).map {
                it.compile(variables, compiled, instancePath, instances)
            }
            ParametrisedSpec.Application(
                    operands,
                    instancePath,
                    type
            )
        }
    }

    private fun Type.extractTypeInstances(
            instances: Instances<Polymorphic<AlgebraicType>>
    ): TypeSig = when (this) {
        is Type.Variable -> TypeSig.Variable(name)
        is Type.Function -> {
            val fromSig = from.extractTypeInstances(instances)
            val toSig = to.extractTypeInstances(instances)
            TypeSig.Function(fromSig, toSig)
        }
        is Type.Application -> {
            val signature = this.signature
            val typeSignature = this.typeSignature
            val name = InstanceName(signature, typeSignature)
            if (instances.containsKey(signature, typeSignature)) {
                // We already have such type
                TypeSig.Application(name)
            } else {
                // Find the base instance
                val names = args.map { it.extractTypeInstances(instances) }
                val typeParams = type.parameters.zip(args).toMap()

                val baseName = InstanceName(type.signature, type.typeSignature)
                var base = instances[baseName]
                if (base == null) {
                    // No base class - add it
                    base = Polymorphic.Base(
                            type,
                            baseName
                    )
                    instances[baseName] = base
                }
                instances.putIfAbsent(signature, typeSignature, Polymorphic.Instance(
                        type.instantiate(typeParams),
                        base,
                        name,
                        type.parameters.zip(names).toMap()
                ))
                TypeSig.Application(name)
            }
        }
    }
}