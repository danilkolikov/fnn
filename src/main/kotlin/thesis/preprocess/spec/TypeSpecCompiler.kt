package thesis.preprocess.spec

import thesis.preprocess.Processor
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.results.Instances

/**
 * Compiles information about algebraic types into their in-memory representations
 *
 * @author Danil Kolikov
 */
class TypeSpecCompiler :
        Processor<Instances<AlgebraicType>, Instances<TypeSpec>> {

    override fun process(data: Instances<AlgebraicType>): Instances<TypeSpec> {
        val result = Instances<TypeSpec>()
        data.forEach { name, typeSignature, type ->
            val constructors = LinkedHashMap<TypeName, TypeSpec.ConstructorInfo>()
            var curOffset = 0
            val newOperands = type.structure.operands.map { operand ->
                when (operand) {
                    is AlgebraicType.Structure.SumOperand.Object -> {
                        val start = curOffset++

                        TypeSpec.Structure.SumOperand.Object(
                                operand.name,
                                start
                        )
                    }
                    is AlgebraicType.Structure.SumOperand.Product -> {
                        val operands = mutableListOf<TypeSpec>()
                        val start = curOffset
                        operand.operands.forEach { thisOperand ->
                            when (thisOperand) {
                                is AlgebraicType.Structure.ProductOperand.Variable ->
                                    throw IllegalStateException(
                                            "All variables should have been resolved in the type ${type.name}"
                                    )
                                is AlgebraicType.Structure.ProductOperand.Application -> {
                                    // This application should be resolved already
                                    val thisTypeSignature = thisOperand.arguments.map { it.toRaw() }
                                    val spec = result[thisOperand.type.signature, thisTypeSignature]
                                            ?: throw IllegalStateException(
                                                    "Type ${thisOperand.type.name} with arguments $thisTypeSignature " +
                                                            "is not instantiated"
                                            )
                                    curOffset += spec.structure.size
                                    operands.add(spec)
                                }
                            }

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
                    name.first(),
                    typeSignature,
                    type,
                    TypeSpec.Structure(newOperands),
                    constructors
            )
            result[name, typeSignature] = spec
            // Add constructors
            newOperands.forEach { operand ->
                constructors[operand.name] = when (operand) {
                    is TypeSpec.Structure.SumOperand.Object -> {
                        TypeSpec.ConstructorInfo(
                                operand.name,
                                type.constructors[operand.name]!!.type,
                                operand.start,
                                emptyList(),
                                spec.structure
                        )
                    }
                    is TypeSpec.Structure.SumOperand.Product -> {
                        TypeSpec.ConstructorInfo(
                                operand.name,
                                type.constructors[operand.name]!!.type,
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