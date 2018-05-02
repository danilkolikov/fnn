package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Kind
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.algebraic.type.RawAlgebraicType
import thesis.preprocess.expressions.type.Parametrised
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
            // Add type to result before inferring of it's structure to support recursive types

            val kind = Kind.createBasic(type.parameters.size)
            val newOperands = mutableListOf<AlgebraicType.Structure.SumOperand>()
            val constructors = LinkedHashMap<TypeName, AlgebraicType.Constructor>()
            val resultType = AlgebraicType(
                    name,
                    type.parameters,
                    kind,
                    AlgebraicType.Structure(newOperands),
                    type.structure.operands.any { it.containsType(name) },
                    constructors
            )
            result[name] = resultType

            // Infer type structure
            type.structure.operands.map { operand ->
                when (operand) {
                    is RawAlgebraicType.Structure.SumOperand.Literal -> AlgebraicType.Structure.SumOperand.Object(
                            operand.name
                    )
                    is RawAlgebraicType.Structure.SumOperand.Product -> AlgebraicType.Structure.SumOperand.Product(
                            operand.name,
                            operand.operands.map { it.toAlgebraicType(result) }
                    )
                }
            }.forEach { newOperands.add(it) }

            // Infer type constructors
            var curConstructor = 0
            newOperands.forEach { operand ->
                val constructorName = operand.name
                val resType = Type.Application(resultType, resultType.parameters.map { Type.Variable(it) })
                val constructorType = when (operand) {
                    is AlgebraicType.Structure.SumOperand.Object -> resType
                    is AlgebraicType.Structure.SumOperand.Product -> operand.operands
                            .foldRight<AlgebraicType.Structure.ProductOperand, Type>(
                                    resType,
                                    { arg, res -> Type.Function(arg.toType(), res) }
                            )
                }
                val parametrised = Parametrised(
                        type.parameters,
                        constructorType,
                        type.parameters.map { it to Type.Variable(it) }.toMap()
                )
                constructors[constructorName] = AlgebraicType.Constructor(
                        parametrised,
                        curConstructor++
                )
            }
        }
        return result
    }

    private fun RawAlgebraicType.Structure.ProductOperand.toAlgebraicType(
            defined: Map<TypeName, AlgebraicType>
    ): AlgebraicType.Structure.ProductOperand = when (this) {
        is RawAlgebraicType.Structure.ProductOperand.Variable ->
            AlgebraicType.Structure.ProductOperand.Variable(
                    name
            )
        is RawAlgebraicType.Structure.ProductOperand.Application ->
            AlgebraicType.Structure.ProductOperand.Application(
                    defined[name] ?: throw UnknownTypeError(name),
                    arguments.map { it.toAlgebraicType(defined) }
            )
    }

    private fun AlgebraicType.Structure.ProductOperand.toType(): Type = when (this) {
        is AlgebraicType.Structure.ProductOperand.Variable -> Type.Variable(name)
        is AlgebraicType.Structure.ProductOperand.Application -> Type.Application(
                type,
                arguments.map { it.toType() }
        )
    }
}