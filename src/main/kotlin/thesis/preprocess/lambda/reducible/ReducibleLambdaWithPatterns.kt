package thesis.preprocess.lambda.reducible

/**
 * Compiled patterns + compiled lambda
 *
 * @author Danil Kolikov
 */
data class ReducibleLambdaWithPatterns(
        val patterns: List<ReduciblePattern>,
        val lambda: ReducibleLambda
)