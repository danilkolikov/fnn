package thesis.preprocess.lambda

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.results.InMemoryExpressions
import thesis.preprocess.results.InMemoryType
import thesis.preprocess.results.InferredLambda

/**
 * Compiles typed lambda-expressions to executable lambda-expressions
 *
 * @author Danil Kolikov
 */
class LambdaCompiler : Processor<InMemoryExpressions, Map<LambdaName, List<CompiledLambda>>> {

    override fun process(data: InMemoryExpressions): Map<LambdaName, List<CompiledLambda>> {
        val memoryRepresentation = TypeDefinitionCompiler().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(memoryRepresentation).process(data.lambdaDefinitions)
    }

    class TypeDefinitionCompiler : Processor<Map<TypeName, InMemoryType>, Map<TypeName, MemoryRepresentation>> {
        override fun process(data: Map<TypeName, InMemoryType>): Map<TypeName, MemoryRepresentation> {
            val constructors = mutableMapOf<TypeName, MemoryRepresentation>()
            data.forEach { name, value ->
                val memoryInfo = value.memoryInfo
                memoryInfo.constructors.mapValues { (_, info) ->
                    if (info.argumentOffsets.isEmpty()) {
                        // It's a object
                        val representation = Array(memoryInfo.typeSize, { it == info.offset })
                        MemoryRepresentation.Object(name, representation)
                    } else {
                        // It's a function
                        MemoryRepresentation.Constructor(name, memoryInfo.typeSize, info)
                    }
                }.forEach { constructorName, info ->
                    constructors[constructorName] = info
                }
            }
            return constructors
        }
    }

    class LambdaDefinitionCompiler(
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>
    ) : Processor<Map<LambdaName, InferredLambda>, Map<LambdaName, List<CompiledLambda>>> {
        override fun process(data: Map<LambdaName, InferredLambda>): Map<LambdaName, List<CompiledLambda>> {
            val result = mutableMapOf<LambdaName, List<CompiledLambda>>()
            data.forEach { name, lambda ->
                val compiled = lambda.expressions.map { it.compile(result) }
                result[name] = compiled
            }
            return result
        }

        private fun Lambda.compile(compiled: Map<LambdaName, List<CompiledLambda>>): CompiledLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable should be made Literal"
            )
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name]
                if (memory != null) {
                    // Some representation of object
                    when (memory) {
                        is MemoryRepresentation.Object -> CompiledLambda.Object(name, memory.representation)
                        is MemoryRepresentation.Constructor -> CompiledLambda.ObjectFunction(name, memory)
                    }
                    // ToDo: Introduce guards to remove this index
                } else defined?.get(0) ?: CompiledLambda.Variable(name)
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