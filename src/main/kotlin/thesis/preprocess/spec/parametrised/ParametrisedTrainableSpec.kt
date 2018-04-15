package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.types.UnsupportedTrainableType

/**
 * Specification for parametrised trainable expression that is instantiated in python code
 *
 * @author Danil Kolikov
 */
data class ParametrisedTrainableSpec(
        val type: Type,
        val typeParameters: Set<TypeVariableName>,
        val argumentsTypes: List<LayerSpec>,
        val resultType: List<LayerSpec>
) {
    sealed class LayerSpec {
        data class Fixed(val size: Int) : LayerSpec()

        data class Variable(val name: LambdaName) : LayerSpec()
    }

    companion object {
        fun fromType(parametrised: Parametrised<Type>): ParametrisedTrainableSpec {
            val type = parametrised.type.instantiate()
            val arguments = type.getArguments()
            val resultType = type.getResultType()

            val args = mergeSignatures(arguments.flatMap { it.toSignatures() })
            val result = mergeSignatures(resultType.toSignatures())
            return ParametrisedTrainableSpec(
                    type,
                    parametrised.parameters.toSet(),
                    args,
                    result
            )
        }

        private fun mergeSignatures(signatures: List<LayerSpec>): List<LayerSpec> {
            // If there are more than 1 consequent Fixed specs, merge them into one
            val result = mutableListOf<LayerSpec>()
            var size = 0
            for (signature in signatures) {
                when (signature) {
                    is LayerSpec.Variable -> {
                        if (size > 0) {
                            result.add(LayerSpec.Fixed(size))
                            size = 0
                        }
                        result.add(signature)
                    }
                    is LayerSpec.Fixed -> {
                        size += signature.size
                    }
                }
            }
            if (size > 0) {
                result.add(LayerSpec.Fixed(size))
            }
            return result
        }

        private fun Type.toSignatures(): List<LayerSpec> = when (this) {
            is Type.Function -> throw UnsupportedTrainableType(this)
            is Type.Variable -> listOf(LayerSpec.Variable(name))
            is Type.Application -> {
                if (!args.isEmpty()) {
                    throw IllegalStateException("Type should have no parameters at this moment")
                }
                type.structure.toSignatures()
            }
        }

        private fun Type.instantiate(): Type = when (this) {
            is Type.Variable -> this
            is Type.Function -> Type.Function(
                    from.instantiate(),
                    to.instantiate()
            )
            is Type.Application -> {
                val instantiated = args.map { it.instantiate() }
                val typeParams = type.parameters.zip(instantiated).toMap()
                Type.Application(
                        type.instantiate(typeParams),
                        emptyList()
                )
            }
        }

        private fun AlgebraicType.Structure.toSignatures(): List<LayerSpec> {
            size?.let {
                // Type has no variables - can return it's size
                return listOf(LayerSpec.Fixed(it))
            }
            // Should inspect structure
            return operands.flatMap {
                val size = it.size
                if (size != null) {
                    return@flatMap listOf(LayerSpec.Fixed(size))
                }
                // Operand contains variables - it's a product
                val product = it as AlgebraicType.Structure.SumOperand.Product
                product.operands.flatMap {
                    when (it) {
                        is AlgebraicType.Structure.ProductOperand.Variable -> listOf(LayerSpec.Variable(it.name))
                        is AlgebraicType.Structure.ProductOperand.Application -> {
                            if (!it.arguments.isEmpty()) {
                                throw IllegalStateException("All arguments should be instantiated")
                            }
                            it.type.structure.toSignatures()
                        }
                    }
                }
            }
        }
    }
}