package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName

/**
 * Type that can contain parameters
 *
 * @author Danil Kolikov
 */
class Parametrised<T: Implication<T>>(
        val parameters: List<TypeVariableName>,
        val type: T
) {

    fun <S: Implication<S>> modifyType(action: (T) -> S) = action(type).bindVariables()

    fun replaceLiterals(map: Map<TypeName, T>) = Parametrised(
            parameters,
            type.replaceLiterals(map)
    )

    fun instantiate(typeParams: Map<TypeVariableName, T>): Parametrised<T> {
        val replaced = type.replace(typeParams)
        return Parametrised(
                replaced.getVariables().toList(),
                replaced
        )
    }

    val isInstantiated = parameters.isEmpty()

    override fun toString() =
            if (parameters.isEmpty()) type.toString() else "@forall ${parameters.joinToString(" ")}. $type"
}