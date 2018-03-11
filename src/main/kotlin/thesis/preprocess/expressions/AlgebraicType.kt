package thesis.preprocess.expressions

/**
 * Algebraic type - can be either a sum, product of types or just a type literal
 */
sealed class AlgebraicType : Expression {

    data class Literal(val name: TypeName) : AlgebraicType()

    data class Sum(
            val operands: List<AlgebraicType>
    ) : AlgebraicType()

    data class Product(
            val name: String,
            val operands: List<AlgebraicType>
    ) : AlgebraicType()
}