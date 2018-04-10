package thesis.preprocess.types

import thesis.preprocess.expressions.algebraic.term.AlgebraicEquation
import thesis.preprocess.expressions.algebraic.term.AlgebraicTerm
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.type.raw.RawType
import thesis.utils.Edge
import thesis.utils.UndirectedGraph
import thesis.utils.solveSystem

fun unifyTypes(
        first: RawType,
        second: RawType,
        definedTypes: Set<String>
): Map<TypeName, RawType> = listOf(first, second).unifyTypes(definedTypes)

fun List<RawType>.unifyTypes(definedTypes: Set<String>): Map<TypeName, RawType> {
    if (size < 2) {
        return emptyMap()
    }
    val terms = map { it.toAlgebraicTerm() }
    val first = terms.first()
    val rest = terms.drop(1)
    return rest.map { AlgebraicEquation(first, it) }
            .inferTypes(definedTypes)
            .mapValues { (_, v) -> RawType.fromAlgebraicTerm(v) }
}

fun Map<String, RawType>.compose(other: Map<String, RawType>, knownTypes: Set<String>): Map<String, RawType> {
    val finalMap = mutableMapOf<String, RawType>()
    val edges = mutableListOf<Edge<String>>()

    listOf(this, other).flatMap { it.entries }.forEach { (name, type) ->
        when (type) {
            is RawType.Literal -> edges.add(Edge(name, type.name))
            is RawType.Variable -> throw IllegalStateException("Unexpected variable ${type.name}")
            is RawType.Function -> finalMap[name] = type
        }
    }
    val graph = UndirectedGraph(edges)
    val components = graph.getConnectedComponents(knownTypes)
    val connectedComponents = components
            .filter { (name, value) -> name != value }
            .map { (name, value) ->
                name to RawType.Literal(value)
            }
            .toMap()
    return connectedComponents + finalMap.mapValues { (_, v) -> v.replaceLiterals(connectedComponents) }
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
        if (right is AlgebraicTerm.Variable) {
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
                name to AlgebraicTerm.Variable(value)
            }
            .toMap()
    return connectedComponents + solution
            .filterKeys {
                !connectedComponents.containsKey(it) && !knownTypes.contains(it)
            }
            .mapValues { (_, value) -> value.replace(connectedComponents) }
}