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

    fun append(arguments: RawArguments) = RawArguments(
            data + arguments.data,
            functions + arguments.functions
    )

    fun getArgumentsBefore(dataPointer: DataPointer) = RawArguments(
            data.subList(0, dataPointer.dataOffset),
            functions.subList(0, dataPointer.functionsCount)
    )

    companion object {
        val EMPTY = RawArguments(
                emptyList(),
                emptyList()
        )
    }
}