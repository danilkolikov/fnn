package thesis.preprocess.expressions.algebraic.term

/**
 * Equation betweeen two algebraic terms
 */
data class AlgebraicEquation(
        val left: AlgebraicTerm,
        val right: AlgebraicTerm
)