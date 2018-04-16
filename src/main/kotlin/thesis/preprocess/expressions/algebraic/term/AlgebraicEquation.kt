package thesis.preprocess.expressions.algebraic.term

/**
 * Equation between two algebraic terms
 *
 * @author Danil Kolikov
 */
data class AlgebraicEquation(
        val left: AlgebraicTerm,
        val right: AlgebraicTerm
) {

    override fun toString() = "$left = $right"
}