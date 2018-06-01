package thesis.preprocess.spec.parametrised

import thesis.preprocess.results.TypeSig

/**
 * Specification of a parametrised trainable expression which is instantiated in python code
 *
 * @author Danil Kolikov
 */
data class ParametrisedTrainableSpec(
        val options: Map<String, Any>,
        val arguments: List<TypeSig>,
        val toType: TypeSig
) {

    val signature: TypeSig
        get() = arguments.foldRight(toType, { sig, res -> TypeSig.Function(sig, res) })

    override fun toString() = "$options: $signature"
}