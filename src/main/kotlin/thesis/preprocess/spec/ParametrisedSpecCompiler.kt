package thesis.preprocess.spec

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.*
import thesis.preprocess.types.UnknownExpressionError
import thesis.utils.NameGenerator

/**
 * Compiles Lambda expressions to specs with parameters
 *
 * @author Danil Kolikov
 */
class ParametrisedSpecCompiler : Processor<InferredExpressions, ParametrisedSpecs> {

    override fun process(data: InferredExpressions): ParametrisedSpecs {
        val typeSpecs = TypeSpecCompiler().process(data.typeDefinitions)

        val instances = LambdaDefinitionCompiler(typeSpecs)
                .process(data.lambdaDefinitions)
        return ParametrisedSpecs(typeSpecs, instances)
    }

    private class LambdaDefinitionCompiler(
            private val typeDefinitions: LinkedHashMap<TypeName, TypeSpec>
    ) : Processor<LinkedHashMap<LambdaName, InferredLambda>, Instances<ParametrisedSpec>> {

        private val nameGenerator = NameGenerator("")

        override fun process(
                data: LinkedHashMap<LambdaName, InferredLambda>
        ): Instances<ParametrisedSpec> {
            val compiled = mutableMapOf<LambdaName, ParametrisedSpec>()
            val instances = Instances<ParametrisedSpec>()

            // Add constructors
            val definedTypes = mutableSetOf<TypeName>()
            typeDefinitions.forEach { _, typeInfo ->
                typeInfo.constructors.forEach { name, info ->
                    val parametrised = info.type    // Don't instantiate as we don't have types with parameters

                    val spec = when (parametrised) {
                        is Type.Variable -> throw IllegalStateException(
                                "Variable $name should have been instantiated"
                        )
                        is Type.Algebraic -> ParametrisedSpec.Object(
                                name,
                                info,
                                parametrised.bindVariables()
                        )
                        is Type.Function -> ParametrisedSpec.Function.Constructor(
                                name,
                                info,
                                parametrised.bindVariables()
                        )
                    }
                    instances.putIfAbsent(spec.instancePath, listOf(), spec)
                    compiled[name] = spec
                }
                definedTypes.add(typeInfo.name)
            }

            // Add expressions
            data.forEach { name, lambda ->
                val cases = mutableListOf<ParametrisedSpec.Function.Guarded.Case>()

                lambda.expressions.forEach { (patterns, lambda) ->

                    val variables = patterns.flatMap { it.getTypedVariables().toList() }
                            .map {
                                it.first to ParametrisedSpec.Variable(
                                        it.first,
                                        listOf(name),
                                        it.second
                                )
                            }
                            .toMap()

                    val case = lambda.compile(
                            variables,
                            compiled,
                            listOf(name),
                            instances
                    )
                    cases.add(ParametrisedSpec.Function.Guarded.Case(
                            patterns,
                            case
                    ))
                }
                val guarded = ParametrisedSpec.Function.Guarded(name, cases, lambda.type)
                instances.putIfAbsent(guarded.instancePath, emptyList(), guarded)
                compiled[name] = guarded
            }
            return instances
        }

        private fun TypedLambda<Type>.compile(
                variables: Map<LambdaName, ParametrisedSpec.Variable>,
                compiled: Map<LambdaName, ParametrisedSpec>,
                instancePath: List<LambdaName>,
                instances: Instances<ParametrisedSpec>
        ): ParametrisedSpec = when (this) {
            is TypedLambda.Trainable -> ParametrisedSpec.Function.Trainable(
                    instancePath,
                    type
            )
            is TypedLambda.Literal -> {
                val defined = compiled[name]?.let {
                    if (instanceTypeParams.isEmpty()) {
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
                        val typeParams = instanceTypeParams.mapValues { (_, v) -> v }
                        val signature = it.instancePath
                        val typeSignature = typeParams.values.map { it.toRaw() }.toList()
                        val instance = instances.get(signature, typeSignature) ?: it.instantiate(
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
                        expression.compile(variables + vars, compiled, instancePath, instances),
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
                val compiledExpr = expression.compile(variables, newCompiled, instancePath, instances)
                ParametrisedSpec.LetAbstraction(
                        compiledExpr,
                        bound,
                        instancePath,
                        compiledExpr.type
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
}