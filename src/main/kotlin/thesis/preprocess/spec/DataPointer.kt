package thesis.preprocess.spec

/**
 * Points on some position in DataBag arrays
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