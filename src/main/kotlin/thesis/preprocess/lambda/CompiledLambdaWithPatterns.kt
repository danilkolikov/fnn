package thesis.preprocess.lambda

/**
 * Compiled patterns + compiled lambda
 *
 * @author Danil Kolikov
 */
data class CompiledLambdaWithPatterns(
        val patterns: List<CompiledPattern>,
        val lambda: CompiledLambda
)