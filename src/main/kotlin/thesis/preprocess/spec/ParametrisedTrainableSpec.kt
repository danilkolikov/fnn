package thesis.preprocess.spec

import thesis.preprocess.expressions.TypeName

/**
 * Specification for parametrised trainable expression that is instantiated in python code
 *
 * @author Danil Kolikov
 */
data class ParametrisedTrainableSpec(
        val argumentTypes: List<TypeName?>,
        val resultType: TypeName?
)