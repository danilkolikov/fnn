package thesis.preprocess.spec

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
class SpecCompiler : Processor<InMemoryExpressions, List<Spec.Function.Guarded>> {

    override fun process(data: InMemoryExpressions): List<Spec.Function.Guarded> {
        val memoryRepresentations = MemoryRepresentationCompiler().process(data.typeDefinitions)
        val unwrapped = AlgebraicTypeUnWrapper().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(
                data.typeDefinitions.map { it.name to it }.toMap(),
                unwrapped,
                memoryRepresentations
        ).process(data.lambdaDefinitions)
    }

    private class AlgebraicTypeUnWrapper : Processor<List<InMemoryType>, Map<TypeName, AlgebraicType>> {
        override fun process(data: List<InMemoryType>): Map<TypeName, AlgebraicType> {
            val replacedTypes = mutableMapOf<TypeName, AlgebraicType>()
            data.forEach { type ->
                val replaced = type.type.replace(replacedTypes)
                replacedTypes[type.name] = replaced
            }
            return replacedTypes
        }
    }

    private class LambdaDefinitionCompiler(
            private val typeMap: Map<TypeName, InMemoryType>,
            private val unwrapped: Map<TypeName, AlgebraicType>,
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>
    ) : Processor<List<InferredLambda>, List<Spec.Function.Guarded>> {
        override fun process(data: List<InferredLambda>): List<Spec.Function.Guarded> {
            val compiled = mutableMapOf<LambdaName, Spec.Function.Guarded>()
            val result = mutableListOf<Spec.Function.Guarded>()
            data.forEach { expression ->
                val name = expression.name
                val cases = mutableListOf<Spec.Function.Guarded.Case>()
                val arguments = expression.type.getArguments()

                expression.expressions.forEach { (patterns, lambda) ->
                    var functionsCount = 0
                    var thisOffset = 0
                    val dataPatterns = mutableListOf<DataPattern>()

                    val variablesMap = mutableMapOf<LambdaName, Spec.Variable>()
                    patterns.zip(arguments) { pattern, type ->
                        when (type) {
                            is Type.Literal -> {
                                val inMemoryType = typeMap[type.name] ?: throw UnknownTypeError(type.name)
                                val start = thisOffset
                                thisOffset += inMemoryType.memoryInfo.typeSize

                                pattern.addVariables(
                                        variablesMap,
                                        dataPatterns,
                                        start to thisOffset,
                                        type.name
                                )
                            }
                            is Type.Function -> {
                                // When argument is function, pattern is variable
                                // (Because we can't pattern-match by function)
                                val functionName = (pattern as Pattern.Variable).name

                                val position = functionsCount++
                                variablesMap[functionName] = Spec.Variable.Function(
                                        functionName,
                                        type,
                                        position
                                )
                            }
                        }
                    }
                    val case = lambda.compile(
                            variablesMap,
                            compiled,
                            expression.expressionTypes,
                            DataPointer(thisOffset, functionsCount)
                    )
                    cases.add(Spec.Function.Guarded.Case(
                            dataPatterns,
                            case
                    ))
                }
                val guarded = Spec.Function.Guarded(name, expression.type, cases)
                compiled[name] = guarded
                result.add(guarded)
            }
            return result
        }

        private fun Pattern.addVariables(
                variables: MutableMap<LambdaName, Spec.Variable>,
                patterns: MutableList<DataPattern>,
                positions: Pair<Int, Int>,
                typeName: TypeName
        ) {
            when (this) {
                is Pattern.Object -> {
                    val memory = memoryRepresentations[name] ?: throw UnknownExpressionError(name)
                    DataPattern.Object(name, positions.first + memory.info.offset)
                }
                is Pattern.Variable -> {
                    val variable = Spec.Variable.Object(Type.Literal(typeName), positions)
                    variables[name] = variable
                    val type = unwrapped[typeName] ?: throw UnknownTypeError(name)
                    patterns.add(
                            DataPattern.Variable(
                                    name,
                                    type.toSpec(positions.first),
                                    positions.first,
                                    positions.second
                            )
                    )
                }
                is Pattern.Constructor -> {
                    val memory = memoryRepresentations[name] ?: throw UnknownExpressionError(name)
                    var offset = positions.first
                    arguments.zip(memory.info.argumentOffsets) { argument, info ->
                        val start = offset
                        offset += info.size
                        argument.addVariables(
                                variables,
                                patterns,
                                start to offset,
                                info.type
                        )
                    }
                }
            }
        }

        private fun Lambda.compile(
                variables: Map<LambdaName, Spec.Variable>,
                compiled: Map<LambdaName, Spec.Function.Guarded>,
                expressionsTypes: Map<Lambda, Type>,
                dataPointer: DataPointer
        ): Spec = when (this) {
            is Lambda.Trainable -> throw IllegalStateException("Trainable should be converted to Literals")
            is Lambda.TypedExpression -> expression.compile(variables, compiled, expressionsTypes, dataPointer)
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name] ?.let {
                    Spec.Variable.External(name, it.type, it)
                }
                val variable = variables[name]
                if (memory != null) {
                    // Some representation of object
                    val type = expressionsTypes[this] ?: throw UnknownExpressionError(name)
                    when (memory) {
                        is MemoryRepresentation.Object -> Spec.Object(type, memory.representation)
                        is MemoryRepresentation.Constructor -> Spec.Function.Constructor(name, type, memory)
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
                val operands = (listOf(function) + this.arguments).map {
                    it.compile(variables, compiled, expressionsTypes, dataPointer)
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
                val data = this.arguments.mapIndexed { index, lambda ->
                    if (expressionsTypes[lambda] is Type.Literal) index else null
                }.filterNotNull()

                // Functions with arguments are functions
                val functions = this.arguments.mapIndexed { index, lambda ->
                    if (expressionsTypes[lambda] is Type.Function) index else null
                }.filterNotNull()

                Spec.Application(
                        type,
                        operands,
                        call,
                        constants,
                        data,
                        functions
                )
            }
        }

        private fun AlgebraicType.toSpec(offset: Int): TypeSpec = when (this) {
            is AlgebraicType.Literal -> TypeSpec.Literal(name, offset)
            is AlgebraicType.Sum -> {
                var curOffset = offset
                val operands = mutableListOf<TypeSpec>()
                for (operand in this.operands) {
                    val spec = operand.toSpec(curOffset)
                    curOffset = spec.end
                    operands.add(spec)
                }
                TypeSpec.Sum(
                        operands,
                        offset,
                        curOffset
                )
            }
            is AlgebraicType.Product -> {
                var curOffset = offset
                val operands = mutableListOf<TypeSpec>()
                for (operand in this.operands) {
                    val spec = operand.toSpec(curOffset)
                    curOffset = spec.end
                    operands.add(spec)
                }
                TypeSpec.Product(
                        name,
                        operands,
                        offset,
                        curOffset
                )
            }
        }
    }
}