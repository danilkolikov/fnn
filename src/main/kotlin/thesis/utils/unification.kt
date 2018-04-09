/**
 *  Unification algorithm for type inference
 */
package thesis.utils

import thesis.preprocess.expressions.algebraic.term.AlgebraicEquation
import thesis.preprocess.expressions.algebraic.term.AlgebraicTerm

fun solveSystem(system: List<AlgebraicEquation>): Map<String, AlgebraicTerm> {
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
            .filter { it.left is AlgebraicTerm.Variable }
            .map { (it.left as AlgebraicTerm.Variable).name to it.right }
            .toMap()
}

private fun switchVariables(equations: List<AlgebraicEquation>) = equations.map {
    val (left, right) = it
    if (left is AlgebraicTerm.Function && right is AlgebraicTerm.Variable) {
        return@map AlgebraicEquation(right, left)
    }
    return@map it
}

private fun removeTrivial(equations: List<AlgebraicEquation>) = equations.filterNot { (left, right) ->
    left is AlgebraicTerm.Variable && right is AlgebraicTerm.Variable && left == right
}

private fun deconstructFunctions(equations: List<AlgebraicEquation>) = equations.flatMap {
    val (left, right) = it
    if (left is AlgebraicTerm.Function && right is AlgebraicTerm.Function) {
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
        val wrong = result.find { e -> e.left is AlgebraicTerm.Variable && e.left != e.right && e.right.hasVariable(e.left) }
        if (wrong != null) {
            throw UnificationError(wrong.left, wrong.right)
        }
        val toReplace = result.find { e ->
            e.left is AlgebraicTerm.Variable && e.left != e.right && result.any {
                it != e && (it.left.hasVariable(e.left) || it.right.hasVariable(e.left))
            }
        } ?: break
        val map = mapOf((toReplace.left as AlgebraicTerm.Variable).name to toReplace.right)
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