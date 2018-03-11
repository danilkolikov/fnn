/**
 *  Unification algorithm for type inference
 */
package thesis.utils

sealed class AlgebraicTerm {
    abstract fun hasVariable(variableTerm: VariableTerm): Boolean
    abstract fun replace(map: Map<VariableTerm, AlgebraicTerm>): AlgebraicTerm
    abstract fun getVariables(): Set<VariableTerm>
}

data class VariableTerm(val name: String) : AlgebraicTerm() {
    override fun hasVariable(variableTerm: VariableTerm) = variableTerm == this
    override fun getVariables() = setOf(this)
    override fun replace(map: Map<VariableTerm, AlgebraicTerm>) = map[this] ?: this

}

data class FunctionTerm(val name: String, val arguments: List<AlgebraicTerm>) : AlgebraicTerm() {
    override fun hasVariable(variableTerm: VariableTerm) = arguments.any { it.hasVariable(variableTerm) }
    override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()
    override fun replace(map: Map<VariableTerm, AlgebraicTerm>) = FunctionTerm(name, arguments.map { it.replace(map) })
}

data class AlgebraicEquation(val left: AlgebraicTerm, val right: AlgebraicTerm)

fun solveSystem(system: List<AlgebraicEquation>): Map<VariableTerm, AlgebraicTerm> {
    var newSystem: List<AlgebraicEquation> = ArrayList(system)
    while (true) {
        val switched = switchVariables(newSystem)
        val noTrivial = removeTrivial(switched)
        val deconstructed = deconstructFunctions(noTrivial)
        val replaced = replaceOccurrences(deconstructed)
        if (replaced == newSystem) {
            break
        }
        newSystem = replaced
    }
    return newSystem
            .filter { it.left is VariableTerm }
            .map { (it.left as VariableTerm) to it.right }
            .toMap()
}

private fun switchVariables(equations: List<AlgebraicEquation>) = equations.map {
    val (left, right) = it
    if (left is FunctionTerm && right is VariableTerm) {
        return@map AlgebraicEquation(right, left)
    }
    return@map it
}

private fun removeTrivial(equations: List<AlgebraicEquation>) = equations.filterNot { (left, right) ->
    left is VariableTerm && right is VariableTerm && left == right
}

private fun deconstructFunctions(equations: List<AlgebraicEquation>) = equations.flatMap {
    val (left, right) = it
    if (left is FunctionTerm && right is FunctionTerm) {
        if (left.name != right.name || left.arguments.size != right.arguments.size) {
            throw UnificationError(left, right)
        }
        return@flatMap left.arguments.zip(right.arguments)
                .map { (left, right) -> AlgebraicEquation(left, right) }
    }
    return@flatMap listOf(it)
}

private fun replaceOccurrences(equations: List<AlgebraicEquation>): List<AlgebraicEquation> {
    var result = equations
    while (true) {
        val wrong = result.find { e -> e.left is VariableTerm && e.left != e.right && e.right.hasVariable(e.left) }
        if (wrong != null) {
            throw UnificationError(wrong.left, wrong.right)
        }
        val toReplace = result.find { e ->
            e.left is VariableTerm && e.left != e.right && result.any {
                it != e && (it.left.hasVariable(e.left) || it.right.hasVariable(e.left))
            }
        } ?: break
        val map = mapOf(toReplace.left as VariableTerm to toReplace.right)
        result = result.map {
            if (it == toReplace) it else
                AlgebraicEquation(
                        it.left.replace(map),
                        it.right.replace(map)
                )
        }
    }
    return result
}

class UnificationError(
        left: AlgebraicTerm,
        right: AlgebraicTerm
) : Exception("Can't unify $left and $right")