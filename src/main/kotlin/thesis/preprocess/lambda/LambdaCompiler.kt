package thesis.preprocess.lambda

import thesis.preprocess.ContextBuilder
import thesis.preprocess.ContextUpdaterByLocal
import thesis.preprocess.LocalTypeContext
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.typeinfo.TypeInfoExtractor

/**
 * Compiles typed lambda-expressions to executable lambda-expressions
 *
 * @author Danil Kolikov
 */
class LambdaCompiler(
        val typeInfoContext: TypeInfoExtractor.Context
) : ContextBuilder<LambdaCompiler.Context> {

    override val context = Context()

    override val typeDefinitionContextUpdater = TypeDefinitionCompiler(context)

    override val lambdaDefinitionContextUpdater = LambdaDefinitionCompiler(context)

    data class Context(
            val memoryRepresentations: MutableMap<TypeName, MemoryRepresentation> = mutableMapOf(),
            val expressions: MutableMap<String, CompiledLambda> = mutableMapOf()
    )

    inner class TypeDefinitionCompiler(
            override val context: Context
    ) : ContextUpdaterByLocal<Context, AlgebraicType> {
        override fun updateContext(localContext: LocalTypeContext<AlgebraicType>) {
            val typeInformation = typeInfoContext.info[localContext.name]!!
            typeInformation.constructors.forEach { name, info ->
                val expression = if (info.argumentOffsets.isEmpty()) {
                    // It's a object
                    val data = Array(typeInformation.typeSize, { it == info.offset })
                    MemoryRepresentation.Object(typeInformation.name, data)
                } else {
                    // It's a function
                    MemoryRepresentation.Constructor(typeInformation.name, typeInformation.typeSize, info)
                }
                context.memoryRepresentations[name] = expression
            }
        }
    }

    inner class LambdaDefinitionCompiler(
            override val context: Context
    ) : ContextUpdaterByLocal<Context, Lambda> {
        override fun updateContext(localContext: LocalTypeContext<Lambda>) {
            val compiled = localContext.expression.compile()
            context.expressions[localContext.name] = compiled
        }

        private fun Lambda.compile(): CompiledLambda = when (this) {
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable should be made Literal"
            )
            is Lambda.Literal -> {
                val memory = context.memoryRepresentations[name]
                val defined = context.expressions[name]
                if (memory != null) {
                    // Some representation of object
                    when (memory) {
                        is MemoryRepresentation.Object -> CompiledLambda.Object(name, memory.representation)
                        is MemoryRepresentation.Constructor -> CompiledLambda.ObjectFunction(name, memory)
                    }
                } else defined ?: CompiledLambda.Variable(name)
            }
            is Lambda.TypedExpression -> expression.compile()
            is Lambda.Abstraction -> CompiledLambda.AnonymousFunction(arguments, expression.compile())
            is Lambda.Application -> CompiledLambda.FunctionCall(
                    function.compile(),
                    arguments.map { it.compile() }
            )
        }
    }

}