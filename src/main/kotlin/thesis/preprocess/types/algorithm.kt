/**
 * Algorithm for type inference
 */
package thesis.preprocess.types

import thesis.preprocess.ast.Definition
import thesis.preprocess.ast.LambdaProgramExpression
import thesis.preprocess.expressions.Expression
import thesis.utils.*

interface TypeContext {
    val nameGenerator: NameGenerator
}

interface LocalTypeContext<out E : LambdaProgramExpression> {
    val expression: E

    val typeScope: Set<String>
    val scope: Map<String, AlgebraicTerm>
    var solution: Map<VariableTerm, AlgebraicTerm>
}

interface ContextUpdater<in E : LambdaProgramExpression> {
    fun updateContext(expression: E)
}

interface TypeContextUpdater<out C : TypeContext, in E : LambdaProgramExpression>
    : ContextUpdater<E> {

    val typeContext: C
}

interface TypeContextUpdaterWithLocal<
        out C : TypeContext,
        E : LambdaProgramExpression,
        L : LocalTypeContext<E>
        > : TypeContextUpdater<C, E> {

    fun getLocalContext(expression: E): L

    fun updateContext(localContext: L)

    override fun updateContext(expression: E) {
        val localContext = getLocalContext(expression)
        updateContext(localContext)
    }
}

abstract class TypeInferenceAlgorithm<
        out C : TypeContext,
        E : Expression,
        L : LocalTypeContext<Definition<E>>
        > : TypeContextUpdaterWithLocal<C, Definition<E>, L> {

    abstract fun E.getEquations(
            localContext: L
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>>

    open fun getEquationsSystem(localContext: L): List<AlgebraicEquation> {
        val newDefinition = localContext.expression
        val (type, equations) = newDefinition.expression.getEquations(localContext)
        return equations + listOf(AlgebraicEquation(
                VariableTerm(newDefinition.name),
                type
        ))
    }

    override fun updateContext(expression: Definition<E>) {
        val localContext = getLocalContext(expression)
        val system = getEquationsSystem(localContext)
        val solution = solveSystem(system)
        localContext.solution = localContext.renameTypes(solution)
        updateContext(localContext)
    }

    private fun L.renameTypes(
            solution: Map<VariableTerm, AlgebraicTerm>
    ): Map<VariableTerm, AlgebraicTerm> {
        // Types with equal names form undirected graph
        // Find connected components and set human-readable names to types in them
        val edges = mutableListOf<Edge<String>>()
        for ((left, right) in solution) {
            if (right is VariableTerm) {
                edges.add(Edge(left.name, right.name))
            }
        }
        val graph = UndirectedGraph(edges)
        val components = graph.getConnectedComponents(typeScope)
        val distinctTypes = typeScope.map { components[it] }.toSet()
        if (distinctTypes.size != typeScope.size) {
            // Some types were clashed during unification, that's definitely a type error
            throw TypeInferenceError(expression.expression, typeContext)
        }
        val connectedComponents = components
                .filter { (name, value) -> name != value }
                .map { (name, value) ->
                    VariableTerm(name) to VariableTerm(value)
                }
                .toMap()
        return connectedComponents + solution
                .filterKeys {
                    !connectedComponents.containsKey(it) && !typeScope.contains(it.name)
                }
                .mapValues { (_, value) -> value.replace(connectedComponents) }
    }
}
