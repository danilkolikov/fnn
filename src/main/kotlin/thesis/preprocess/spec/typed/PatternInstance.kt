package thesis.preprocess.spec.typed

/**
 * Typed version of Data Pattern
 *
 * @author Danil Kolikov
 */
sealed class PatternInstance {

    data class Constructor(
            val position: Int,
            val operands: List<PatternInstance>
    ): PatternInstance()

    data class Literal(
            val position: Int
    ): PatternInstance()

    class Variable: PatternInstance()
}