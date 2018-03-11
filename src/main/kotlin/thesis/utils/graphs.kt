/**
 * Utility graph algorithms
 */
package thesis.utils

data class Edge<out T>(val from: T, val to: T)

class UndirectedGraph<T>(
        val edges: MutableMap<T, MutableList<T>>
) {
    constructor() : this(mutableMapOf())

    constructor(edges: List<Edge<T>>) : this() {
        edges.forEach { (from, to) ->
            this.edges.computeIfAbsent(from, { mutableListOf() }).add(to)
            this.edges.computeIfAbsent(to, { mutableListOf() }).add(from)
        }
    }

    fun getConnectedComponents(startFrom: Set<T>): Map<T, T> {
        val visited = mutableSetOf<T>()
        val result = mutableMapOf<T, T>()
        for (vertex in startFrom) {
            if (visited.contains(vertex)) {
                continue
            }
            dfs(vertex, visited).forEach { result[it] = vertex }
        }
        return result
    }

    private fun dfs(start: T, visited: MutableSet<T>): Set<T> {
        visited.add(start)
        return setOf(start) + (edges[start] ?: mutableListOf())
                .filterNot { visited.contains(it) }
                .flatMap { dfs(it, visited) }
    }
}