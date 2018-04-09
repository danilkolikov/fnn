package thesis.preprocess.expressions.lambda

/**
 * Typed expression
 *
 * @param T   Type of Type expression is typed with
 * @author Danil Kolikov
 */
interface Typed<out T> {
    val type: T
}