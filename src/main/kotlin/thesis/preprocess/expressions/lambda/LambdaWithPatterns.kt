package thesis.preprocess.expressions.lambda

/**
 * Lambda with patterns
 *
 * @author Danil Kolikov
 */
data class LambdaWithPatterns<out L : Lambda, out P : Pattern>(
        val patterns: List<P>,
        val lambda: L,
        val isTailRecursive: Boolean = false
) {
    override fun toString() = "${patterns.joinToString(" ")} = $lambda"
}
