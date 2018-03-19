/**
 * Contexts for type inference and processing of program
 */
package thesis.preprocess

import thesis.preprocess.ast.Definition
import thesis.preprocess.ast.LambdaProgramExpression
import thesis.preprocess.expressions.Expression

interface ContextUpdater<out C> {
    val context: C
}

interface ContextUpdaterByExpression<out C, in E : LambdaProgramExpression> : ContextUpdater<C> {

    fun updateContext(expression: E)
}

interface ContextUpdaterByLocal<out C, E : Expression> : ContextUpdater<C> {

    fun updateContext(localContext: LocalTypeContext<E>)
}

interface TypeContextUpdater<out C : TypeContext, in E : LambdaProgramExpression>
    : ContextUpdaterByExpression<C, E>

interface TypeContextUpdaterWithLocal<
        out C : TypeContext,
        E : Expression
        > : TypeContextUpdater<C, Definition<E>>, ContextUpdaterByLocal<C, E> {

    val dependent: List<ContextUpdaterByLocal<*, E>>

    fun Definition<E>.getLocalContext(): LocalTypeContext<E>

    override fun updateContext(expression: Definition<E>) {
        val localContext = expression.getLocalContext()
        updateContext(localContext)
        dependent.forEach { it.updateContext(localContext) }
    }
}

class NoOpContextUpdaterByLocal<out C, E: Expression>(
        override val context: C
) : ContextUpdaterByLocal<C, E> {
    override fun updateContext(localContext: LocalTypeContext<E>) {
    }
}
