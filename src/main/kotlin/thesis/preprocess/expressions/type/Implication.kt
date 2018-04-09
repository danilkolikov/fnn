package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName

/**
 * Interface for structures similar to implication
 *
 * @author Danil Kolikov
 */
interface Implication<T: Implication<T>> : Replaceable<T> {

    fun getVariables(): Set<TypeVariableName>

    fun bindVariables(): Parametrised<T>

    fun replaceLiterals(map: Map<TypeName, T>): T

    fun getOperands(): List<T>

    fun getArguments() = getOperands().dropLast(1)

    fun getResultType() = getOperands().last()

}