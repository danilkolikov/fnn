/**
 * Some extensions for types
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.results.TypeSignature
import thesis.preprocess.results.toSignature

fun Parametrised<Type>.instantiate(
        typeParams: Map<TypeVariableName, Type>
): Parametrised<Type> {
    val replaced = type.instantiate(typeParams)
    // TODO: Do some kind of compose here
    val newParams = this.typeParams.mapValues { (_, v) -> v.replace(typeParams) } + typeParams
    return Parametrised(
            replaced.getVariables().toList(),
            replaced,
            newParams,
            initialParameters
    )
}

fun Parametrised<Type>.typeSignature(): TypeSignature = initialParameters.map { typeParams[it]!!.toSignature() }