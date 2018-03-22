package thesis.preprocess.types

import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName
import thesis.utils.*

internal const val FUNCTION_SIGN = "→"

fun Type.toAlgebraicTerm(): AlgebraicTerm = when (this) {
    is Type.Literal -> VariableTerm(name)
    is Type.Function -> FunctionTerm(FUNCTION_SIGN, listOf(from.toAlgebraicTerm(), to.toAlgebraicTerm()))
}

fun AlgebraicTerm.toType(): Type = when (this) {
    is VariableTerm -> Type.Literal(name)
    is FunctionTerm -> Type.Function(
            arguments.first().toType(),
            arguments.last().toType()
    )
}

fun List<AlgebraicEquation>.inferTypes(knownTypes: Set<TypeName>): Map<String, AlgebraicTerm> {
    val solution = solveSystem(this)
    return renameTypes(solution, knownTypes)
}

private fun renameTypes(
        solution: Map<String, AlgebraicTerm>,
        knownTypes: Set<TypeName>
): Map<String, AlgebraicTerm> {
    // Types with equal names form undirected graph
    // Find connected components and set human-readable names to types in them
    val edges = mutableListOf<Edge<String>>()
    for ((left, right) in solution) {
        if (right is VariableTerm) {
            edges.add(Edge(left, right.name))
        }
    }
    val graph = UndirectedGraph(edges)
    val components = graph.getConnectedComponents(knownTypes)
    val distinctTypes = knownTypes.map { components[it] }.toSet()
    if (distinctTypes.size != knownTypes.size) {
        // Some types were clashed during unification, that's definitely a type error
        throw TypeInferenceErrorWithoutContext()
    }
    val connectedComponents = components
            .filter { (name, value) -> name != value }
            .map { (name, value) ->
                name to VariableTerm(value)
            }
            .toMap()
    return connectedComponents + solution
            .filterKeys {
                !connectedComponents.containsKey(it) && !knownTypes.contains(it)
            }
            .mapValues { (_, value) -> value.replace(connectedComponents) }
}