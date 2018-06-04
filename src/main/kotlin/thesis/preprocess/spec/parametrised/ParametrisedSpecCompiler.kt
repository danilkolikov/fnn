package thesis.preprocess.spec.parametrised

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InferredExpressions
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.ParametrisedSpecs
import thesis.preprocess.types.UnknownExpressionError

/**
 * Compiles Lambda expressions to specs with parameters
 *
 * @author Danil Kolikov
 */
class ParametrisedSpecCompiler : Processor<InferredExpressions, ParametrisedSpecs> {

    override fun process(data: InferredExpressions): ParametrisedSpecs {
        val compiled = mutableMapOf<LambdaName, ParametrisedSpec>()
        val instances = LinkedHashMap<InstanceSignature, ParametrisedSpec>()

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
                instances.putIfAbsent(listOf(name), spec)
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
            instances.putIfAbsent(listOf(name), result)
            compiled[name] = result
        }

        return ParametrisedSpecs(
                data.typeDefinitions,
                instances
        )
    }

    private fun TypedLambda<Type>.compile(
            variables: Map<LambdaName, ParametrisedSpec.Variable>,
            compiled: Map<LambdaName, ParametrisedSpec>,
            instancePath: List<LambdaName>,
            instances: LinkedHashMap<InstanceSignature, ParametrisedSpec>
    ): ParametrisedSpec = when (this) {
        is TypedLambda.Trainable -> {
            val arguments = type.type.getArguments()
            val toType = type.type.getResultType()
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
                    instances.putIfAbsent(listOf(name), it)
                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            it,
                            emptyMap(),
                            it.instancePath,
                            instancePath,
                            it.type
                    )
                } else {
                    // Should instantiate
                    val typeParams = type.typeParams
                    val signature = it.instancePath

                    instances.putIfAbsent(signature, it)

                    ParametrisedSpec.Function.Polymorphic(
                            name,
                            it,
                            typeParams,
                            signature,
                            instancePath,
                            it.type
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
}