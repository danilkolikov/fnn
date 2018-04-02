package thesis.eval

/**
 * Result of evaluation of Spec. Can be either data or function
 *
 * @author Danil Kolikov
 */
sealed class EvalResult {

    data class Data(val data: List<Short>) : EvalResult()

    data class Function(val spec: EvalSpec.Function) : EvalResult()
}