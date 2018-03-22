/**
 * Representations of lambda definitions
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.Type

interface SimpleLambda {
    val expressions: List<Lambda>
}

interface RenamedLambda : SimpleLambda {
    val nameMap: Map<String, Lambda>
}

interface InferredLambda : RenamedLambda {
    val type: Type
    val expressionTypes: Map<Lambda, Type>
}

// Implementations

data class SimpleLambdaImpl(
        override val expressions: List<Lambda>
) : SimpleLambda

data class RenamedLambdaImpl(
        private val _lambda: SimpleLambda,
        override val nameMap: Map<String, Lambda>
) : RenamedLambda, SimpleLambda by _lambda

data class InferredLambdaImpl(
        private val _lambda: RenamedLambda,
        override val type: Type,
        override val expressionTypes: Map<Lambda, Type>
) : InferredLambda, RenamedLambda by _lambda