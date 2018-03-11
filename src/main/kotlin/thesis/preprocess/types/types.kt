package thesis.preprocess.types

/**
 * A simple type - either literal or function
 */

sealed class SimpleType

data class TypeTerminal(val name: String) : SimpleType() {
    override fun toString() = name
}

data class TypeFunction(val from: SimpleType, val to: SimpleType) : SimpleType() {
    override fun toString() = "($from $FUNCTION_SIGN $to)"
}

fun SimpleType.toAlgebraicTerm(): AlgebraicTerm = when (this) {
    is TypeTerminal -> VariableTerm(name)
    is TypeFunction -> FunctionTerm(FUNCTION_SIGN, listOf(from.toAlgebraicTerm(), to.toAlgebraicTerm()))
}

fun AlgebraicTerm.toSimpleType(): SimpleType = when (this) {
    is VariableTerm -> TypeTerminal(name)
    is FunctionTerm -> TypeFunction(
            arguments.first().toSimpleType(),
            arguments.last().toSimpleType()
    )
}