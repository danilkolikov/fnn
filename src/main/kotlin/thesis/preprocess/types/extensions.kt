package thesis.preprocess.types

import thesis.preprocess.expressions.Type
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

internal const val FUNCTION_SIGN = "→"

fun Type.toAlgebraicTerm(): AlgebraicTerm = when (this) {
    is Type.Literal -> VariableTerm(name)
    is Type.Function -> FunctionTerm(FUNCTION_SIGN, listOf(from.toAlgebraicTerm(), to.toAlgebraicTerm()))
}

fun AlgebraicTerm.toSimpleType(): Type = when (this) {
    is VariableTerm -> Type.Literal(name)
    is FunctionTerm -> Type.Function(
            arguments.first().toSimpleType(),
            arguments.last().toSimpleType()
    )
}
