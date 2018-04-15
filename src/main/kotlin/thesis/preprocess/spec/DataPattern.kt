package thesis.preprocess.spec

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.TypeSignature

/**
 * Representation of pattern
 *
 * @author Danil Kolikov
 */
sealed class DataPattern {

    data class Object(
            val name: TypeName,
            val position: Int
    ) : DataPattern() {
        override fun toString() = "[$name: $position]"
    }

    data class Variable(
            val name: LambdaName,
            val typeName: TypeName,
            val type: TypeSpec,
            val signature: InstanceSignature,
            val typeSignature: TypeSignature,
            val start: Int,
            val end: Int
    ) : DataPattern() {
        override fun toString() = "[$name: ($start, $end)]"
    }
}