package thesis.preprocess.spec

import thesis.preprocess.expressions.TypeName

/**
 * Specification of algebraic type for pattern matching
 *
 * @author Danil Kolikov
 */
sealed class TypeSpec {

    abstract val start: Int

    abstract val end: Int

    data class Literal(
            val name: TypeName,
            override val start: Int
    ) : TypeSpec() {
        override val end = start + 1

        override fun toString() = "($name: $start)"
    }

    data class Sum(
            val operands: List<TypeSpec>,
            override val start: Int,
            override val end: Int
    ) : TypeSpec() {
        override fun toString() = "(Sum: ($start, $end), ${operands.joinToString(" ")})"
    }

    data class Product(
            val name: TypeName,
            val operands: List<TypeSpec>,
            override val start: Int,
            override val end: Int
    ) : TypeSpec() {
        override fun toString() = "($name: ($start, $end), ${operands.joinToString(" ")})"
    }
}