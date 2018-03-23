package thesis.preprocess.lambda

import thesis.preprocess.Processor
import thesis.preprocess.expressions.*
import thesis.preprocess.results.InMemoryExpressions
import thesis.preprocess.results.InMemoryType
import thesis.preprocess.results.InferredLambda

/**
 * Compiles typed lambda-expressions to executable lambda-expressions
 *
 * @author Danil Kolikov
 */
class LambdaCompiler : Processor<InMemoryExpressions, Map<LambdaName, CompiledLambda>> {

    override fun process(data: InMemoryExpressions): Map<LambdaName, CompiledLambda.GuardedFunction> {
        val memoryRepresentation = TypeDefinitionCompiler().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(memoryRepresentation).process(data.lambdaDefinitions)
    }

    class TypeDefinitionCompiler : Processor<List<InMemoryType>, Map<TypeName, MemoryRepresentation>> {
        override fun process(data: List<InMemoryType>): Map<TypeName, MemoryRepresentation> {
            val constructors = mutableMapOf<TypeName, MemoryRepresentation>()
            data.forEach { value ->
                val name = value.name
                val memoryInfo = value.memoryInfo
                memoryInfo.constructors.map { info ->
                    info.name to if (info.argumentOffsets.isEmpty()) {
                        // It's an object
                        val representation = Array(
                                memoryInfo.typeSize,
                                { (if (it == info.offset) 1 else 0).toShort() }
                        )
                        MemoryRepresentation.Object(name, info, representation)
                    } else {
                        // It's a function
                        MemoryRepresentation.Constructor(name, info, memoryInfo.typeSize)
                    }
                }.forEach { (constructorName, info) ->
                    constructors[constructorName] = info
                }
            }
            return constructors
        }
    }

    class LambdaDefinitionCompiler(
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>
    ) : Processor<List<InferredLambda>, Map<LambdaName, CompiledLambda.GuardedFunction>> {
        override fun process(data: List<InferredLambda>): Map<LambdaName, CompiledLambda.GuardedFunction> {
            val result = mutableMapOf<LambdaName, CompiledLambda.GuardedFunction>()
            data.forEach { value ->
                val name = value.name
                val compiled = value.expressions.map { it.compile(result) }
                result[name] = CompiledLambda.GuardedFunction(compiled)
            }
            return result
        }

        private fun LambdaWithPatterns.compile(
                compiled: Map<LambdaName, CompiledLambda>
        ): CompiledLambdaWithPatterns = CompiledLambdaWithPatterns(
                patterns.map { it.compile() },
                lambda.compile(compiled)
        )

        private fun Pattern.compile(): CompiledPattern = when (this) {
            is Pattern.Variable -> CompiledPattern.Variable(name)
            is Pattern.Object -> {
                val constructor = memoryRepresentations[name]!!
                CompiledPattern.Object(constructor.info)
            }
            is Pattern.Constructor -> {
                val constructor = memoryRepresentations[name]!!
                CompiledPattern.Constructor(
                        arguments.map { it.compile() },
                        constructor.info
                )
            }
        }

        private fun Lambda.compile(compiled: Map<LambdaName, CompiledLambda>): CompiledLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable should be made Literal"
            )
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name]
                if (memory != null) {
                    // Some representation of object
                    when (memory) {
                        is MemoryRepresentation.Object -> CompiledLambda.Object(memory.typeName, memory.representation)
                        is MemoryRepresentation.Constructor -> CompiledLambda.ObjectFunction(name, memory)
                    }
                } else defined ?: CompiledLambda.Variable(name)
            }
            is Lambda.TypedExpression -> expression.compile(compiled)
            is Lambda.Abstraction -> CompiledLambda.AnonymousFunction(arguments, expression.compile(compiled))
            is Lambda.Application -> CompiledLambda.FunctionCall(
                    function.compile(compiled),
                    arguments.map { it.compile(compiled) }
            )
        }
    }
}