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

    fun <S : Implication<S>> modifyType(action: (T) -> S): Parametrised<S> {
        val result = action(type)
        val variables = result.getVariables()
        val newParams = typeParams.mapValues { (_, v) -> action(v) }
        return Parametrised(
                variables.toList(),
                result,
                newParams,
                initialParameters
        )
    }

    val isInstantiated = parameters.isEmpty()

    override fun toString() =
            if (parameters.isEmpty()) type.toString() else "@forall ${parameters.joinToString(" ")}. $type"
}