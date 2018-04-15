package thesis.preprocess.expressions

/**
 * Kind of a type
 *
 * @author Danil Kolikov
 */
sealed class Kind {
    /**
     * The only kind - *
     *
     * @author Danil Kolikov
     */
    object Star : Kind() {
        override fun toString() = "*"
    }

    data class Function(val from: Kind, val to: Kind) : Kind() {
        override fun toString() = "$from → $to"
    }

    companion object {
        /**
         * We support only types whose parameters are of first kind (no kind-to-kind functions)
         * So the result kind will be just a function whose arguments are * and the result is *
         */
        fun createBasic(argumentsCount: Int) = (0 .. argumentsCount)
                .map { Star }
                .reduceRight<Kind, Kind> { s, acc ->
                    Function(s, acc)
                }
    }
}