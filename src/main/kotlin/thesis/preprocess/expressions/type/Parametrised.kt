package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.TypeVariableName

/**
 * Type that can contain parameters
 *
 * @author Danil Kolikov
 */
open class Parametrised<T : Implication<T>>  (
        val parameters: List<TypeVariableName>,
        val type: T,
        val typeParams: Map<TypeVariableName, T>,
        val initialParameters: List<TypeVariableName> = parameters
) {

    fun <S : Implication<S>> modifyType(
            preserveParameters: Boolean = true,
            action: (T) -> S
    ): Parametrised<S> {
        val result = action(type)
        val variables = result.getVariables()
        val newTypeParams = typeParams.mapValues { (_, v) -> action(v) }
        val newParameters = variables.toList()
        return Parametrised(
                newParameters,
                result,
                newTypeParams,
                if (preserveParameters) initialParameters else newParameters
        )
    }

    val isInstantiated = parameters.isEmpty()

    override fun toString() =
            if (parameters.isEmpty()) type.toString() else "@forall ${parameters.joinToString(" ")}. $type"
}