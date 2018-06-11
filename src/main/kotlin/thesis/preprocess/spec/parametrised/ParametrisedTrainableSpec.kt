package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.type.Type

/**
 * Specification of a parametrised trainable expression which is instantiated in python code
 *
 * @author Danil Kolikov
 */
data class ParametrisedTrainableSpec(
        val options: Map<String, Any>,
        val arguments: List<Type>,
        val toType: Type
) {

    override fun toString() = "$options"
}