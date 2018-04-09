package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.algebraic.type.AlgebraicTypeStructure
import thesis.preprocess.expressions.algebraic.type.RawAlgebraicType
import thesis.preprocess.expressions.type.Type

/**
 * Infers type information for algebraic types
 *
 * @author Danil Kolikov
 */
class AlgebraicTypeInferenceProcessor :
        Processor<LinkedHashMap<TypeName, RawAlgebraicType>, LinkedHashMap<TypeName, AlgebraicType>> {
    override fun process(
            data: LinkedHashMap<TypeName, RawAlgebraicType>
    ): LinkedHashMap<TypeName, AlgebraicType> {
        val result = LinkedHashMap<TypeName, AlgebraicType>()
        data.forEach { (name, type) ->
            val newOperands = type.operands.map { operand ->
                when (operand) {
                    is RawAlgebraicType.SumOperand.Literal -> AlgebraicTypeStructure.SumOperand.Object(
                            operand.name
                    )
                    is RawAlgebraicType.SumOperand.Product -> AlgebraicTypeStructure.SumOperand.Product(
                            operand.name,
                            operand.operands.map { result[it.name] ?: throw UnknownTypeError(it.name) }
                    )
                }
            }
            val constructors = LinkedHashMap<TypeName, Type>()
            val resultType = AlgebraicType(
                    name,
                    AlgebraicTypeStructure(newOperands),
                    constructors
            )
            newOperands.forEach { operand ->
                val constructorName = operand.name
                val constructorType = when (operand) {
                    is AlgebraicTypeStructure.SumOperand.Object -> Type.Algebraic(resultType)
                    is AlgebraicTypeStructure.SumOperand.Product -> operand.operands.foldRight<AlgebraicType, Type>(
                            Type.Algebraic(resultType),
                            { arg, res -> Type.Function(Type.Algebraic(arg), res) }
                    )
                }
                constructors[constructorName] = constructorType
            }
            result[name] = resultType
        }
        return result
    }
}