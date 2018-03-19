/**
 * Local contexts for type inference
 */
package thesis.preprocess

import thesis.preprocess.expressions.Expression
import thesis.preprocess.types.TypeInferenceErrorWithoutContext
import thesis.utils.*

/**
 * Base context
 */
class LocalTypeContext<E : Expression>(
        val name: String,
        val expression: E,
        val nameMap: Map<String, E>,
        val typeScope: Set<String>,
        val system: List<AlgebraicEquation>,
        val expressionScope: Map<String, AlgebraicTerm>,
        val expressionTypes: Map<E, AlgebraicTerm>
) {
    val solution: Map<VariableTerm, AlgebraicTerm> by lazy {
        val solution = solveSystem(system)
        renameTypes(solution)
    }

    private fun renameTypes(
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
            throw TypeInferenceErrorWithoutContext(expression)
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