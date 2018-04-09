package thesis.preprocess.expressions.algebraic.type

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.TypeName

/**
 * AST version of Algebraic type - it is a sum of either literal or product.
 * Every product has 1 or more literals. Literals are not resolved to types
 */
data class RawAlgebraicType(
        val operands: List<SumOperand>
) : Expression {

    sealed class SumOperand {

        abstract val name: TypeName

        data class Literal(
                override val name: TypeName
        ) : SumOperand()

        data class Product(
                override val name: TypeName,
                val operands: List<Literal>
        ) : SumOperand()
    }
}