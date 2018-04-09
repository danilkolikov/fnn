/**
 * Parametrized typed pattern
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.spec

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.type.Type

typealias ParametrizedPattern = TypedPattern<Type>

fun ParametrizedPattern.instantiate(typeParams: Map<TypeVariableName, Type>): ParametrizedPattern = when (this) {
    is TypedPattern.Object -> TypedPattern.Object(
            pattern,
            type.instantiate(typeParams)
    )
    is TypedPattern.Variable -> TypedPattern.Variable(
            pattern,
            type.instantiate(typeParams)
    )
    is TypedPattern.Constructor -> TypedPattern.Constructor(
            name,
            arguments.map { it.instantiate(typeParams) },
            type.instantiate(typeParams)
    )
}