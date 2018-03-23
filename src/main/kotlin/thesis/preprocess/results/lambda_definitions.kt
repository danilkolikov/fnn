/**
 * Representations of lambda definitions
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.*

interface SimpleLambda {
    val name: LambdaName
    val expressions: List<LambdaWithPatterns>
}

interface RenamedLambda : SimpleLambda {
    val nameMap: Map<String, Lambda>
    val patternsNameMap: Map<String, Pattern>
}

interface InferredLambda : RenamedLambda {
    val type: Type
    val expressionTypes: Map<Lambda, Type>
}

// Implementations

data class SimpleLambdaImpl(
        override val name: LambdaName,
        override val expressions: List<LambdaWithPatterns>
) : SimpleLambda

data class RenamedLambdaImpl(
        private val _lambda: SimpleLambda,
        override val nameMap: Map<String, Lambda>,
        override val patternsNameMap: Map<String, Pattern>
) : RenamedLambda, SimpleLambda by _lambda

data class InferredLambdaImpl(
        private val _lambda: RenamedLambda,
        override val type: Type,
        override val expressionTypes: Map<Lambda, Type>
) : InferredLambda, RenamedLambda by _lambda