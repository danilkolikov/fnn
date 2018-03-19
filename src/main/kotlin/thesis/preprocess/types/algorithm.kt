/**
 * Algorithm for type inference
 */
package thesis.preprocess.types

import thesis.preprocess.LocalTypeContext
import thesis.preprocess.TypeContext
import thesis.preprocess.TypeContextUpdaterWithLocal
import thesis.preprocess.ast.Definition
import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.TypeName
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.VariableTerm
import java.util.*

abstract class TypeInferenceAlgorithm<
        out C : TypeContext,
        E : Expression
        > : TypeContextUpdaterWithLocal<C, E> {

    abstract fun E.rename(): Pair<E, Map<String, E>>

    abstract fun Definition<E>.getTypeScope(): Set<TypeName>

    abstract fun E.getScope(): Map<String, AlgebraicTerm>

    abstract fun E.getEquations(
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<E, AlgebraicTerm>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>>

    open fun getEquations(
            name: String,
            expression: E,
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<E, AlgebraicTerm>
    ): List<AlgebraicEquation> {
        val (type, equations) = expression.getEquations(scope, expressionTypes)
        return equations + listOf(AlgebraicEquation(
                VariableTerm(name),
                type
        ))
    }

    override fun Definition<E>.getLocalContext(): LocalTypeContext<E> {
        val (renamed, nameMap) = expression.rename()
        val typeScope = getTypeScope()
        val scope = renamed.getScope()
        val expressionTypes = IdentityHashMap<E, AlgebraicTerm>()
        val system = getEquations(name, renamed, scope, expressionTypes)
        return LocalTypeContext(
                name,
                renamed,
                nameMap,
                typeScope,
                system,
                scope,
                expressionTypes
        )
    }
}
