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
    override fun toString() = "$name ${parameters.joinToString(" ")} = $structure"

    data class Structure(
            val operands: List<SumOperand>
    ) : Expression {

        override fun toString() = operands.joinToString(" | ")

        sealed class SumOperand {

            abstract val name: TypeName

            abstract fun containsType(typeName: TypeName): Boolean

            data class Literal(
                    override val name: TypeName
            ) : SumOperand() {
                override fun toString() = name

                override fun containsType(typeName: TypeName) = false
            }

            data class Product(
                    override val name: TypeName,
                    val operands: List<ProductOperand>
            ) : SumOperand() {
                override fun toString() = "$name ${operands.joinToString(" ")}"

                override fun containsType(typeName: TypeName) = operands.any { it.containsType(typeName) }
            }
        }

        sealed class ProductOperand {

            abstract val name: String

            abstract fun containsType(typeName: TypeName): Boolean

            data class Variable(
                    override val name: String
            ) : ProductOperand() {
                override fun toString() = name

                override fun containsType(typeName: TypeName) = false
            }

            data class Application(
                    override val name: String,
                    val arguments: List<ProductOperand>
            ) : ProductOperand() {
                override fun toString() = "($name ${arguments.joinToString(" ")}"

                override fun containsType(typeName: TypeName) = name == typeName ||
                        arguments.any { it.containsType(typeName )}
            }
        }
    }
}