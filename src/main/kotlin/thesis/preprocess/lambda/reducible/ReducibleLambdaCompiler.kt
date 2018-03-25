package thesis.preprocess.lambda.reducible

import thesis.preprocess.Processor
import thesis.preprocess.expressions.*
import thesis.preprocess.lambda.MemoryRepresentation
import thesis.preprocess.lambda.MemoryRepresentationCompiler
import thesis.preprocess.results.InMemoryExpressions
import thesis.preprocess.results.InferredLambda

/**
 * Compiles typed lambda-expressions to executable lambda-expressions
 *
 * @author Danil Kolikov
 */
class ReducibleLambdaCompiler : Processor<InMemoryExpressions, Map<LambdaName, ReducibleLambda>> {

    override fun process(data: InMemoryExpressions): Map<LambdaName, ReducibleLambda.GuardedFunction> {
        val memoryRepresentation = MemoryRepresentationCompiler().process(data.typeDefinitions)
        return LambdaDefinitionCompiler(memoryRepresentation).process(data.lambdaDefinitions)
    }

    private class LambdaDefinitionCompiler(
            private val memoryRepresentations: Map<TypeName, MemoryRepresentation>
    ) : Processor<List<InferredLambda>, Map<LambdaName, ReducibleLambda.GuardedFunction>> {
        override fun process(data: List<InferredLambda>): Map<LambdaName, ReducibleLambda.GuardedFunction> {
            val result = mutableMapOf<LambdaName, ReducibleLambda.GuardedFunction>()
            data.forEach { value ->
                val name = value.name
                val compiled = value.expressions.map { it.compile(result) }
                result[name] = ReducibleLambda.GuardedFunction(compiled)
            }
            return result
        }

        private fun LambdaWithPatterns.compile(
                compiled: Map<LambdaName, ReducibleLambda>
        ): ReducibleLambdaWithPatterns = ReducibleLambdaWithPatterns(
                patterns.map { it.compile() },
                lambda.compile(compiled)
        )

        private fun Pattern.compile(): ReduciblePattern = when (this) {
            is Pattern.Variable -> ReduciblePattern.Variable(name)
            is Pattern.Object -> {
                val constructor = memoryRepresentations[name]!!
                ReduciblePattern.Object(constructor.info)
            }
            is Pattern.Constructor -> {
                val constructor = memoryRepresentations[name]!!
                ReduciblePattern.Constructor(
                        arguments.map { it.compile() },
                        constructor.info
                )
            }
        }

        private fun Lambda.compile(compiled: Map<LambdaName, ReducibleLambda>): ReducibleLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable should be made Literal"
            )
            is Lambda.Literal -> {
                val memory = memoryRepresentations[name]
                val defined = compiled[name]
                if (memory != null) {
                    // Some representation of object
                    when (memory) {
                        is MemoryRepresentation.Object -> ReducibleLambda.Object(memory.typeName, memory.representation.toTypedArray())
                        is MemoryRepresentation.Constructor -> ReducibleLambda.ObjectFunction(name, memory)
                    }
                } else defined ?: ReducibleLambda.Variable(name)
            }
            is Lambda.TypedExpression -> expression.compile(compiled)
            is Lambda.Abstraction -> ReducibleLambda.AnonymousFunction(arguments, expression.compile(compiled))
            is Lambda.Application -> ReducibleLambda.FunctionCall(
                    function.compile(compiled),
                    arguments.map { it.compile(compiled) }
            )
        }
    }
}