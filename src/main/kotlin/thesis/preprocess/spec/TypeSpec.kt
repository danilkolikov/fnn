package thesis.preprocess.spec

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.TypeSignature

/**
 * Specification of algebraic type for pattern matching
 *
 * @author Danil Kolikov
 */
data class TypeSpec(
        val name: TypeName,
        val typeSignature: TypeSignature,
        val type: AlgebraicType,
        val structure: Structure,
        val constructors: LinkedHashMap<TypeName, ConstructorInfo>
) {

    val signature: InstanceSignature
        get() = listOf(name)

    data class ConstructorInfo(
            val name: TypeName,
            val type: Type,
            val start: Int,
            val arguments: List<TypeSpec>,
            val toType: Structure
    )

    data class Structure(
            val operands: List<SumOperand>
    ) : InMemoryType {

        override val start = operands.first().start
        override val end = operands.last().end

        sealed class SumOperand : InMemoryType {

            abstract val name: TypeName

            data class Object(
                    override val name: TypeName,
                    override val start: Int
            ) : SumOperand() {
                override val end = start + 1
            }

            data class Product(
                    override val name: TypeName,
                    val operands: List<TypeSpec>,
                    override val start: Int
            ) : SumOperand() {
                override val end = start + operands.map { it.structure.size }.sum()
            }
        }
    }
}