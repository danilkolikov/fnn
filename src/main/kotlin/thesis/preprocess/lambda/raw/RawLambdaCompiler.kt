package thesis.preprocess.lambda.raw

import thesis.preprocess.Processor
import thesis.preprocess.expressions.*
import thesis.preprocess.lambda.MemoryRepresentation
import thesis.preprocess.lambda.MemoryRepresentationCompiler
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
class RawLambdaCompiler : Processor<InMemoryExpressions, Map<LambdaName, RawLambda.Function.Guarded>> {

    override fun process(data: InMemoryExpressions): Map<LambdaName, RawLambda.Function.Guarded> {
        val memoryRepresentations = MemoryRepresentationCompiler().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(
                data.typeDefinitions.map { it.name to it }.toMap(),
                memoryRepresentations
        ).process(data.lambdaDefinitions)
    }

    private class LambdaDefinitionCompiler(
            private val typeMap: Map<TypeName, InMemoryType>,
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>
    ) : Processor<List<InferredLambda>, Map<LambdaName, RawLambda.Function.Guarded>> {
        override fun process(data: List<InferredLambda>): Map<LambdaName, RawLambda.Function.Guarded> {
            val result = mutableMapOf<LambdaName, RawLambda.Function.Guarded>()
            data.forEach { expression ->
                val name = expression.name
                val cases = mutableListOf<RawLambda.Function.Guarded.Case>()
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
                                pattern.addVariables(
                                        variablesMap,
                                        guards,
                                        start to offset,
                                        inMemoryType.name
                                )
                            }
                            is Type.Function -> {
                                // When argument is function, pattern is variable
                                // (Because we can't pattern-match by function)
                                val functionName = (pattern as Pattern.Variable).name

                                val position = functionsCount++
                                variablesMap[functionName] = RawLambda.Variable.Function(
                                        functionName,
                                        type,
                                        position,
                                        DataPointer.START
                                )
                            }
                        }
                    }
                    val compiled = lambda.compile(
                            variablesMap,
                            result,
                            expression.expressionTypes,
                            DataPointer(offset, functionsCount)
                    )
                    cases.add(RawLambda.Function.Guarded.Case(
                            guards,
                            compiled
                    ))
                }
                result[name] = RawLambda.Function.Guarded(name, expression.type, cases)
            }
            return result
        }

        private fun Pattern.addVariables(
                variables: MutableMap<LambdaName, RawLambda.Variable>,
                guards: MutableList<Guard>,
                positions: Pair<Int, Int>,
                typeName: TypeName
        ) {
            when (this) {
                is Pattern.Object -> {
                    val memory = memoryRepresentations[name] ?: throw UnknownExpressionError(name)
                    val guard = Guard.Object(positions, memory as MemoryRepresentation.Object)
                    guards.add(guard)
                }
                is Pattern.Variable -> {
                    val variable = RawLambda.Variable.Object(Type.Literal(typeName), positions, DataPointer.START)
                    variables[name] = variable
                    val guard = Guard.Variable(positions)
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
                                start to offset,
                                info.type
                        )
                    }
                }
            }
        }

        private fun Lambda.compile(
                variables: Map<LambdaName, RawLambda.Variable>,
                compiled: Map<LambdaName, RawLambda.Function.Guarded>,
                expressionsTypes: Map<Lambda, Type>,
                dataPointer: DataPointer
        ): RawLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException("Trainable should be converted to Literals")
            is Lambda.TypedExpression -> expression.compile(variables, compiled, expressionsTypes, dataPointer)
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name]
                val variable = variables[name]
                if (memory != null) {
                    // Some representation of object
                    val type = expressionsTypes[this] ?: throw UnknownExpressionError(name)
                    when (memory) {
                        is MemoryRepresentation.Object -> RawLambda.Object(type, memory.representation)
                        is MemoryRepresentation.Constructor -> RawLambda.Function.Constructor(name, type, memory)
                    }
                } else defined ?: variable ?: throw UnknownExpressionError(name)
            }
            is Lambda.Abstraction -> {
                val type = expressionsTypes[this] ?: throw TypeInferenceErrorWithoutContext()
                val arguments = type.getArguments()
                var thisOffset = dataPointer.dataOffset
                var functionsCounter = dataPointer.functionsCount
                val newVariables = this.arguments.zip(arguments) { name, argType ->
                    when (argType) {
                        is Type.Literal -> {
                            // Object - save positions of representation in array
                            val memory = typeMap[argType.name] ?: throw UnknownExpressionError(name)
                            val start = thisOffset
                            thisOffset += memory.memoryInfo.typeSize
                            val positions = start to thisOffset
                            name to RawLambda.Variable.Object(
                                    argType,
                                    positions,
                                    dataPointer
                            )
                        }
                        is Type.Function -> {
                            // Some function - save position in input
                            name to RawLambda.Variable.Function(name, argType, functionsCounter++, dataPointer)
                        }
                    }
                }.toMap()

                RawLambda.Function.Anonymous(
                        type,
                        expression.compile(
                                variables + newVariables,
                                compiled,
                                expressionsTypes,
                                DataPointer(thisOffset, functionsCounter)
                        ),
                        dataPointer
                )
            }
            is Lambda.Application -> {
                val type = expressionsTypes[this] ?: throw TypeInferenceErrorWithoutContext()
                RawLambda.Application(
                        type,
                        function.compile(variables, compiled, expressionsTypes, dataPointer),
                        this.arguments.map {
                            it.compile(variables, compiled, expressionsTypes, dataPointer)
                        },
                        dataPointer
                )
            }
        }
    }
}