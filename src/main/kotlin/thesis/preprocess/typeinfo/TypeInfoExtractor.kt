package thesis.preprocess.typeinfo

import thesis.preprocess.ContextBuilder
import thesis.preprocess.ContextUpdaterByLocal
import thesis.preprocess.LocalTypeContext
import thesis.preprocess.NoOpContextUpdaterByLocal
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.types.toSimpleType

/**
 * Extracts memory-specific information about context from type definitions
 *
 * @author Danil Kolikov
 */
class TypeInfoExtractor : ContextBuilder<TypeInfoExtractor.Context> {

    override val context = Context()

    override val typeDefinitionContextUpdater = TypeInfoContextUpdater(context)

    override val lambdaDefinitionContextUpdater: ContextUpdaterByLocal<Context, Lambda>
        get() = NoOpContextUpdaterByLocal(context)

    data class Context(
            val info: MutableMap<TypeName, TypeInformation> = mutableMapOf()
    )

    class TypeInfoContextUpdater(override val context: Context) : ContextUpdaterByLocal<Context, AlgebraicType> {
        override fun updateContext(localContext: LocalTypeContext<AlgebraicType>) {
            val constructors = localContext.expression.getConstructors().map {
                val term = localContext.expressionScope[it]!!
                val type = localContext.solution[term]!!
                it to type.toSimpleType()
            }
            context.info[localContext.name] = TypeInformation(
                    localContext.name,
                    constructors,
                    context.info
            )
        }
    }
}