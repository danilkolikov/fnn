package thesis.eval

import thesis.preprocess.spec.TypeSpec

/**
 * Information about how does this object corresponds to type
 *
 * @author Danil Kolikov
 */
sealed class DataTypeInformation {

    abstract val presence: Short

    data class Literal(
         val spec: TypeSpec.Literal,
         override val presence: Short
    ) : DataTypeInformation()

    data class Sum(
            val spec: TypeSpec.Sum,
            val operands: List<DataTypeInformation>,
            override val presence: Short
    ) : DataTypeInformation()

    data class Product(
            val spec: TypeSpec.Product,
            val operands: List<DataTypeInformation>,
            override val presence: Short
    ) : DataTypeInformation()
}