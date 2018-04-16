package thesis.preprocess.types

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.term.AlgebraicEquation
import thesis.preprocess.expressions.algebraic.term.AlgebraicTerm
import thesis.preprocess.expressions.type.raw.RawType
import thesis.utils.Edge
import thesis.utils.UndirectedGraph
import thesis.utils.solveSystem

/**
 * Unifies two RawType-s to check that they have the same structure
 */
fun unifyTypes(
        first: RawType,
        second: RawType,
        definedTypes: Set<String>
): Map<TypeName, RawType> = listOf(first, second).unifyTypes(definedTypes)

/**
 * Unifies list of RawType-s to check that they all have the same structure
 */
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

/**
 * Merges substitutions received from unification algorithm
 */
fun mergeSubstitutions(
        subs: List<Map<String, RawType>>,
        knownTypes: Set<TypeName>
): Map<String, RawType> {
    // Substitutions can intersect only by variables
    val intersection = mutableMapOf<String, MutableList<RawType>>()
    subs.forEach { substitution ->
        substitution.forEach { name, type ->
            intersection.putIfAbsent(name, mutableListOf())
            intersection[name]?.add(type)
        }
    }
    val result = mutableMapOf<String, RawType>()
    val equations = intersection.map { (variable, list) ->
        if (list.size == 1) {
            // Expression appears once - can add to result without unification
            result[variable] = list.first()
            emptyList()
        } else {
            // The same expression appears in many substitutions - have to unify
            val first = list.first().toAlgebraicTerm()
            list.drop(1).map { AlgebraicEquation(first, it.toAlgebraicTerm()) }
        }
    }.flatMap { it }
    val solution = equations.inferTypes(knownTypes).mapValues { (_, v) -> RawType.fromAlgebraicTerm(v) }
    return result.mapValues { (_, v) -> v.replace(solution) } + solution
}

/**
 * Solves system of lambda equations and renames types according to the set of defined types
 */
fun List<AlgebraicEquation>.inferTypes(knownTypes: Set<TypeName>): Map<String, AlgebraicTerm> {
    val solution = solveSystem(this)
    return renameTypes(solution, knownTypes)
}

/**
 * Splits set of types to group of equal types and creates a new substitution, in which these types are
 * equal to previously defined ones
 */
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
        } else {
            if (knownTypes.contains(left)) {
                // Type = Function type - definitely an error
                throw TypeInferenceErrorWithoutContext()
            }
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