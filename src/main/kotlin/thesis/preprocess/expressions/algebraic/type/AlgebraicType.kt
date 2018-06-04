package thesis.preprocess.expressions.algebraic.type

import thesis.preprocess.expressions.*
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.instantiate
import thesis.preprocess.results.InstanceSignature

/**
 * Representation of algebraic type. Has inferred kind, structure and types of constructors
 *
 * @author Danil Kolikov
 */
class AlgebraicType(
        val name: TypeName,
        val parameters: List<TypeVariableName>,
        val kind: Kind,
        val structure: Structure,
        val recursive: Boolean,
        val constructors: LinkedHashMap<TypeName, Constructor>
) {

    val signature: InstanceSignature
        get() = listOf(name)

    override fun toString() = "$name ${parameters.joinToString(" ")} = $structure"

    data class Constructor(
            val type: Parametrised<Type>,
            val position: Int
    )

    data class Structure(
            val operands: List<SumOperand>
    ) : Expression {

        val size: Int?
            get() {
                val sizes = operands.map { it.size }
                return if (sizes.any { it == null }) null else sizes.filterNotNull().sum()
            }

        fun getVariables() = operands.flatMap { it.getVariables() }.toSet()

        override fun toString() = operands.joinToString(" | ")

        sealed class SumOperand {

            abstract val name: TypeName

            abstract fun replaceVariables(map: Map<TypeVariableName, ProductOperand>): SumOperand

            abstract fun getVariables(): Set<TypeVariableName>

            abstract val size: Int?

            data class Object(
                    override val name: TypeName
            ) : SumOperand() {

                override fun replaceVariables(map: Map<TypeVariableName, ProductOperand>) = this

                override fun getVariables() = emptySet<TypeVariableName>()

                override val size: Int?
                    get() = 1

                override fun toString() = name
            }

            data class Product(
                    override val name: TypeName,
                    val operands: List<ProductOperand>
            ) : SumOperand() {

                override fun replaceVariables(map: Map<TypeVariableName, ProductOperand>) = Product(
                        name,
                        operands.map { it.replace(map) }
                )

                override fun getVariables() = operands.flatMap { it.getVariables() }.toSet()

                override val size: Int?
                    get() {
                        val sizes = operands.map { it.size }
                        return if (sizes.any { it == null }) null else sizes.filterNotNull().sum()
                    }

                override fun toString() = "$name ${operands.joinToString(" ")}"
            }
        }

        sealed class ProductOperand : Replaceable<ProductOperand> {

            abstract fun getVariables(): Set<TypeVariableName>

            abstract fun toType(): Type

            abstract val size: Int?

            data class Variable(
                    val name: TypeVariableName
            ) : ProductOperand() {

                override fun replace(map: Map<String, ProductOperand>) = map[name] ?: this

                override fun toType() = Type.Variable(name)

                override fun getVariables() = setOf(name)

                override val size: Int?
                    get() = null

                override fun toString() = name
            }

            data class Application(
                    val type: AlgebraicType,
                    val arguments: List<ProductOperand>
            ) : ProductOperand() {

                override fun replace(map: Map<String, ProductOperand>) = Application(
                        type,
                        arguments.map { it.replace(map) }
                )

                override fun toType() = Type.Application(type, arguments.map { it.toType() })

                override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

                override val size: Int?
                    get() {
                        if (!arguments.isEmpty()) {
                            throw IllegalStateException(
                                    "Can't compute size of type application"
                            )
                        }
                        return type.structure.size
                    }

                override fun toString() = if (arguments.isEmpty()) type.name
                else "(${type.name} ${arguments.joinToString(" ")})"
            }
        }
    }
}
