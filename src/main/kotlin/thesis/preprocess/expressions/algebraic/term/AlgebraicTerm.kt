package thesis.preprocess.expressions.algebraic.term

import thesis.preprocess.expressions.Replaceable

/**
 * Algebraic term - can be either a variable or a function
 *
 * @author Danil Kolikov
 */
sealed class AlgebraicTerm : Replaceable<AlgebraicTerm> {

    abstract fun hasVariable(variable: Variable): Boolean

    abstract fun getVariables(): Set<Variable>

    data class Variable(val name: String) : AlgebraicTerm() {

        override fun hasVariable(variable: Variable) = variable == this

        override fun getVariables() = setOf(this)

        override fun replace(map: Map<String, AlgebraicTerm>) = map[this.name] ?: this

        override fun toString() = name
    }

    data class Function(val name: String, val arguments: List<AlgebraicTerm>) : AlgebraicTerm() {

        override fun hasVariable(variable: Variable) = arguments.any { it.hasVariable(variable) }

        override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

        override fun replace(map: Map<String, AlgebraicTerm>) = Function(name, arguments.map { it.replace(map) })

        override fun toString() = "$name(${arguments.joinToString(", ")})"
    }
}
