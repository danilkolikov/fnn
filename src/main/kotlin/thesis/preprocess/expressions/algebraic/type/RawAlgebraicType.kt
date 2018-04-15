package thesis.preprocess.expressions.algebraic.type

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName

/**
 * Raw algebraic type which we get from parser. We support only types whose arguments are of first kind
 *
 * @author Danil Kolikov
 */
data class RawAlgebraicType(
        val name: TypeName,
        val parameters: List<TypeVariableName>,
        val structure: Structure
) {
    data class Structure(
            val operands: List<SumOperand>
    ) : Expression {

        sealed class SumOperand {

            abstract val name: TypeName

            data class Literal(
                    override val name: TypeName
            ) : SumOperand()

            data class Product(
                    override val name: TypeName,
                    val operands: List<ProductOperand>
            ) : SumOperand()
        }

        sealed class ProductOperand {

            abstract val name: String

            data class Variable(
                    override val name: String
            ) : ProductOperand()

            data class Application(
                    override val name: String,
                    val arguments: List<ProductOperand>
            ) : ProductOperand()
        }
    }
}