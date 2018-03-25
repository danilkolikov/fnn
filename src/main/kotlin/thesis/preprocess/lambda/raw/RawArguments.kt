package thesis.preprocess.lambda.raw

/**
 * Arguments for raw lambda call
 *
 * @author Danil Kolikov
 */
data class RawArguments(
        val data: List<Short>,
        val functions: List<RawLambda.Function>
) {
    companion object {
        val EMPTY = RawArguments(
                emptyList(),
                emptyList()
        )
    }
}