package thesis.preprocess.expressions

/**
 * Lambda with patterns
 *
 * @author Danil Kolikov
 */
data class LambdaWithPatterns(
        val patterns: List<Pattern>,
        val lambda: Lambda
) {
    override fun toString() = "${patterns.joinToString(" ")} = $lambda"
}