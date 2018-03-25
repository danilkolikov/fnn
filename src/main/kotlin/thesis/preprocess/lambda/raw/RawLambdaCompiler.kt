package thesis.preprocess.lambda.raw

import thesis.preprocess.Processor
import thesis.preprocess.expressions.*
import thesis.preprocess.lambda.MemoryRepresentation
import thesis.preprocess.lambda.MemoryRepresentationCompiler
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.results.InMemoryExpressions
import thesis.preprocess.results.InMemoryType
import thesis.preprocess.results.InferredLambda
import thesis.preprocess.types.TypeInferenceErrorWithoutContext
import thesis.preprocess.types.UnknownExpressionError
import thesis.preprocess.types.UnknownTypeError

/**
 * Compiles Lambda expressions to ones that can be called with raw arguments
 *
 * @author Danil Kolikov
 */
class RawLambdaCompiler(
        val nameGenerator: NameGenerator
) : Processor<InMemoryExpressions, Map<LambdaName, RawLambda.Function.Guarded>> {

    override fun process(data: InMemoryExpressions): Map<LambdaName, RawLambda.Function.Guarded> {
        val memoryRepresentations = MemoryRepresentationCompiler().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(
                data.typeDefinitions.map { it.name to it }.toMap(),
                memoryRepresentations,
                nameGenerator
        ).process(data.lambdaDefinitions)
    }

    private class LambdaDefinitionCompiler(
            private val typeMap: Map<TypeName, InMemoryType>,
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>,
            private val nameGenerator: NameGenerator
    ) : Processor<List<InferredLambda>, Map<LambdaName, RawLambda.Function.Guarded>> {
        override fun process(data: List<InferredLambda>): Map<LambdaName, RawLambda.Function.Guarded> {
            val result = mutableMapOf<LambdaName, RawLambda.Function.Guarded>()
            data.forEach { expression ->
                val name = expression.name
                val lambdas = mutableListOf<RawLambda.Function.Anonymous>()
                val arguments = expression.type.getArguments()
                expression.expressions.forEach { (patterns, lambda) ->
                    var offset = 0
                    var functionsCount = 0
                    val guards = mutableListOf<Guard>()
                    val variablesMap = mutableMapOf<LambdaName, RawLambda.Variable>()
                    patterns.zip(arguments) { pattern, type ->
                        when (type) {
                            is Type.Literal -> {
                                // Argument is a simple type - we can pattern-match
                                val inMemoryType = typeMap[type.name] ?: throw UnknownTypeError(type.name)
                                val start = offset
                                offset += inMemoryType.memoryInfo.typeSize
                                pattern.addVariables(variablesMap, guards, start to offset)
                            }
                            is Type.Function -> {
                                // When argument is function, pattern is variable
                                // (Because we can't pattern-match by function)
                                val functionName = (pattern as Pattern.Variable).name

                                val position = functionsCount++
                                guards.add(Guard.Function(position))
                                variablesMap[functionName] = RawLambda.Variable.Function(
                                        functionName,
                                        position
                                )
                            }
                        }
                    }
                    val compiled = lambda.compile(variablesMap, result, expression.expressionTypes)
                    lambdas.add(RawLambda.Function.Anonymous(
                            guards,
                            compiled
                    ))
                }
                result[name] = RawLambda.Function.Guarded(name, lambdas)
            }
            return result
        }

        private fun Pattern.addVariables(
                variables: MutableMap<LambdaName, RawLambda.Variable>,
                guards: MutableList<Guard>,
                positions: Pair<Int, Int>
        ) {
            when (this) {
                is Pattern.Object -> {
                    val memory = memoryRepresentations[name] ?: throw UnknownExpressionError(name)
                    val guard = Guard.Data.Object(positions, memory as MemoryRepresentation.Object)
                    guards.add(guard)
                }
                is Pattern.Variable -> {
                    val variable = RawLambda.Variable.Object(name, positions)
                    variables[name] = variable
                    val guard = Guard.Data.Variable(positions)
                    guards.add(guard)
                }
                is Pattern.Constructor -> {
                    val memory = memoryRepresentations[name] ?: throw UnknownExpressionError(name)
                    var offset = positions.first
                    arguments.zip(memory.info.argumentOffsets) { argument, info ->
                        val start = offset
                        offset += info.size
                        argument.addVariables(
                                variables,
                                guards,
                                start to offset
                        )
                    }
                }
            }
        }

        private fun Lambda.compile(
                variables: Map<LambdaName, RawLambda.Variable>,
                compiled: Map<LambdaName, RawLambda.Function.Guarded>,
                expressionsTypes: Map<Lambda, Type>
        ): RawLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException("Trainable should be converted to Literals")
            is Lambda.TypedExpression -> expression.compile(variables, compiled, expressionsTypes)
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name]
                val variable = variables[name]
                if (memory != null) {
                    // Some representation of object
                    when (memory) {
                        is MemoryRepresentation.Object -> RawLambda.Object(memory.typeName, memory.representation)
                        is MemoryRepresentation.Constructor -> RawLambda.Function.Constructor(memory.typeName, memory)
                    }
                } else defined ?: variable ?: throw UnknownExpressionError(name)
            }
            is Lambda.Abstraction -> {
                val type = expressionsTypes[this] ?: throw TypeInferenceErrorWithoutContext()
                val arguments = type.getArguments()
                var offset = 0
                var functionsCounter = 0
                val guards = mutableListOf<Guard>()
                val newVariables = this.arguments.zip(arguments) { name, argType ->
                    when (argType) {
                        is Type.Literal -> {
                            // Object - save positions of representation in array
                            val memory = typeMap[argType.name] ?: throw UnknownExpressionError(name)
                            val start = offset
                            offset += memory.memoryInfo.typeSize
                            val positions = start to offset
                            guards.add(Guard.Data.Variable(positions))
                            name to RawLambda.Variable.Object(
                                    argType.name,
                                    positions
                            )
                        }
                        is Type.Function -> {
                            // Some function - save position in input
                            val position = functionsCounter++
                            guards.add(Guard.Function(position))
                            name to RawLambda.Variable.Function(name, position)
                        }
                    }
                }.toMap()
                RawLambda.Function.Anonymous(
                        guards,
                        expression.compile(
                                variables + newVariables,
                                compiled,
                                expressionsTypes
                        )
                )
            }
            is Lambda.Application -> {
                val type = expressionsTypes[function] ?: throw TypeInferenceErrorWithoutContext()
                val arguments = type.getArguments()
                if (arguments.size == this.arguments.size) {
                    // Full application
                    RawLambda.Application(
                            function.compile(variables, compiled, expressionsTypes),
                            this.arguments.map { it.compile(variables, compiled, expressionsTypes) }
                    )
                } else {
                    // Partial application
                    // Use eta-reduction to transform it to a usual function application
                    val rest = List(arguments.size - this.arguments.size, { nameGenerator.next("x") })
                    val eta = Lambda.Abstraction(
                            rest,
                            Lambda.Application(
                                    function,
                                    this.arguments + rest.map { Lambda.Literal(it) }
                            )
                    )
                    val newTypes = expressionsTypes + mapOf(eta to type)
                    eta.compile(variables, compiled, newTypes)
                }
            }
        }
    }
}