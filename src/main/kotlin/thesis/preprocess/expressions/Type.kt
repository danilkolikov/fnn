package thesis.preprocess.expressions

/**
 * A simple type - either literal or function
 */
sealed class Type : Expression {

    abstract fun getOperands(): List<Type>

    fun getArguments() = getOperands().dropLast(1)

    data class Literal(val name: String) : Type() {
        override fun toString() = name

        override fun getOperands() = listOf(this)
    }

    data class Function(val from: Type, val to: Type) : Type() {
        override fun toString() = "($from → $to)"

        override fun getOperands() = listOf(this.from) + to.getOperands()
    }
}