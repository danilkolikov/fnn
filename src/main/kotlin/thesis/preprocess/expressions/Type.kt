package thesis.preprocess.expressions

/**
 * A simple type - either literal or function
 */
sealed class Type : Expression {

    data class Literal(val name: String) : Type() {
        override fun toString() = name
    }

    data class Function(val from: Type, val to: Type) : Type() {
        override fun toString() = "($from → $to)"
    }
}