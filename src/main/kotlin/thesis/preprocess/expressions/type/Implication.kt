package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName

/**
 * Interface for structures similar to type
 *
 * @author Danil Kolikov
 */
interface Implication<T : Implication<T>> : Replaceable<T>, WithVariables {

    fun replaceLiterals(map: Map<TypeName, T>): T

    fun getOperands(): List<T>

    val isObject: Boolean
        get() = getArguments().isEmpty()

    fun getArguments() = getOperands().dropLast(1)

    fun getResultType() = getOperands().last()

}