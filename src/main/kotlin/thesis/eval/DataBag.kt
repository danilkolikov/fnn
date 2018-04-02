package thesis.eval

import thesis.preprocess.spec.DataPointer

/**
 * Arguments for raw lambda eval
 *
 * @author Danil Kolikov
 */
data class DataBag(
        val data: List<Short>,
        val functions: List<EvalSpec.Function>
) {

    fun append(arguments: DataBag) = DataBag(
            data + arguments.data,
            functions + arguments.functions
    )

    fun getArgumentsBefore(dataPointer: DataPointer) = DataBag(
            data.subList(0, dataPointer.dataOffset),
            functions.subList(0, dataPointer.functionsCount)
    )

    companion object {
        val EMPTY = DataBag(
                emptyList(),
                emptyList()
        )
    }
}