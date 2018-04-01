package thesis.preprocess.lambda.raw

/**
 * Points on some position in RawArguments arrays
 *
 * @author Danil Kolikov
 */
data class DataPointer(
        val dataOffset: Int,
        val functionsCount: Int
) {
    companion object {
        val START = DataPointer(0, 0)
    }
}