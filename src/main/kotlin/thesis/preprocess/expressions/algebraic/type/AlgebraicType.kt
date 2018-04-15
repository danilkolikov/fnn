package thesis.preprocess.expressions.algebraic.type

import thesis.preprocess.expressions.*
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.instantiate
import thesis.preprocess.expressions.type.raw.RawType

/**
 * Representation of algebraic type
 *
 * @author Danil Kolikov
 */
class AlgebraicType(
        val name: TypeName,
        val parameters: List<TypeVariableName>,
        val kind: Kind,
        val structure: Structure,
        val constructors: LinkedHashMap<TypeName, Parametrised<Type>>
) {

    fun instantiate(map: Map<TypeVariableName, Type>): AlgebraicType {
        val operandsMap = map.mapValues { (_, v) -> v.toOperand() }
        val replacedStructure = structure.replaceVariables(operandsMap)
        val variables = replacedStructure.getVariables()
        val kind = Kind.createBasic(variables.size)
        val newConstructors = constructors
                .mapValuesTo(LinkedHashMap()) { (_, v) -> v.instantiate(map) }
        return AlgebraicType(
                name,
                variables.toList(),
                kind,
                replacedStructure,
                newConstructors
        )
    }

    val signature: List<String>
        get() = listOf(name)

    override fun toString() = "$name ${parameters.joinToString(" ")}"

    data class Structure(
            val operands: List<SumOperand>
    ) : Expression {

        fun replaceVariables(map: Map<TypeVariableName, ProductOperand>) = Structure(
                operands.map { it.replaceVariables(map) }
        )

        val size: Int?
            get() {
                val sizes = operands.map { it.size }
                return if (sizes.any { it == null }) null else sizes.filterNotNull().sum()
            }

        fun getVariables() = operands.flatMap { it.getVariables() }.toSet()

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
            }
        }

        sealed class ProductOperand : Replaceable<ProductOperand> {

            abstract fun getVariables(): Set<TypeVariableName>

            abstract fun toRaw(): RawType

            abstract val size: Int?

            data class Variable(
                    val name: TypeVariableName
            ) : ProductOperand() {
                override fun replace(map: Map<String, ProductOperand>) = map[name] ?: this
                override fun getVariables() = setOf(name)
                override fun toRaw() = RawType.Variable(name)

                override val size: Int?
                    get() = null
            }

            data class Application(
                    val type: AlgebraicType,
                    val arguments: List<ProductOperand>
            ) : ProductOperand() {
                override fun replace(map: Map<String, ProductOperand>) = Application(
                        type,
                        arguments.map { it.replace(map) }
                )

                override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

                override fun toRaw() = RawType.Application(
                        type.name,
                        arguments.map { it.toRaw() }
                )

                override val size: Int?
                    get() {
                        if (!arguments.isEmpty()) {
                            throw IllegalStateException(
                                    "Can't compute size of type application"
                            )
                        }
                        return type.structure.size
                    }
            }
        }
    }

    private fun Type.toOperand(): Structure.ProductOperand = when (this) {
        is Type.Variable -> Structure.ProductOperand.Variable(name)
        is Type.Application -> Structure.ProductOperand.Application(
                type,
                args.map { it.toOperand() }
        )
        is Type.Function -> throw UnsupportedOperationException(
                "Can't instantiate algebraic type by function type"
        )
    }
}
