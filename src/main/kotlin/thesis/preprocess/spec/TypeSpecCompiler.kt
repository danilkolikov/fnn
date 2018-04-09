package thesis.preprocess.spec

import thesis.preprocess.Processor
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.algebraic.type.AlgebraicTypeStructure
import thesis.preprocess.types.UnknownTypeError

/**
 * Compiles information about algebraic types into their in-memory representations
 *
 * @author Danil Kolikov
 */
class TypeSpecCompiler :
        Processor<LinkedHashMap<TypeName, AlgebraicType>, LinkedHashMap<TypeName, TypeSpec>> {
    override fun process(data: LinkedHashMap<TypeName, AlgebraicType>): LinkedHashMap<TypeName, TypeSpec> {
        val result = LinkedHashMap<TypeName, TypeSpec>()
        data.forEach { name, type ->
            val constructors = LinkedHashMap<TypeName, TypeSpec.ConstructorInfo>()
            var curOffset = 0
            val newOperands = type.structure.operands.map { operand ->
                when (operand) {
                    is AlgebraicTypeStructure.SumOperand.Object -> {
                        val start = curOffset++

                        TypeSpec.Structure.SumOperand.Object(
                                operand.name,
                                start
                        )
                    }
                    is AlgebraicTypeStructure.SumOperand.Product -> {
                        val operands = mutableListOf<TypeSpec>()
                        val start = curOffset
                        operand.operands.forEach {
                            val spec = result[it.name] ?: throw UnknownTypeError(it.name)
                            curOffset += spec.structure.size
                            operands.add(spec)
                        }
                        TypeSpec.Structure.SumOperand.Product(
                                operand.name,
                                operands,
                                start
                        )
                    }
                }
            }
            val spec = TypeSpec(
                    name,
                    TypeSpec.Structure(newOperands),
                    constructors
            )
            result[name] = spec
            // Add constructors
            newOperands.forEach { operand ->
                constructors[operand.name] = when (operand) {
                    is TypeSpec.Structure.SumOperand.Object -> {
                         TypeSpec.ConstructorInfo(
                                operand.name,
                                type.constructors[operand.name]!!,
                                operand.start,
                                emptyList(),
                                spec.structure
                        )
                    }
                    is TypeSpec.Structure.SumOperand.Product -> {
                        TypeSpec.ConstructorInfo(
                                operand.name,
                                type.constructors[operand.name]!!,
                                operand.start,
                                operand.operands,
                                spec.structure
                        )
                    }
                }
            }
        }
        return result
    }
}