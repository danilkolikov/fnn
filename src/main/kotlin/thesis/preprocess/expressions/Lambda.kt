package thesis.preprocess.expressions

/**
 * Lambda-expression
 */
sealed class Lambda : Expression {

    abstract fun getBoundVariables(): Set<String>

    data class Literal(val name: LambdaName) : Lambda() {
        override fun getBoundVariables() = emptySet<String>()

        override fun toString() = name
    }

    data class TypedExpression(
            val expression: Lambda,
            val type: Type
    ) : Lambda() {
        override fun getBoundVariables() = expression.getBoundVariables()

        override fun toString() = "($expression : $type)"
    }

    object Trainable : Lambda() {
        override fun getBoundVariables() = emptySet<String>()

        override fun toString() = "@learn"
    }

    data class Abstraction(
            val arguments: List<LambdaName>,
            val expression: Lambda
    ) : Lambda() {
        override fun getBoundVariables() = arguments.toSet() + expression.getBoundVariables()

        override fun toString() = "(\\${arguments.joinToString(" ")}. $expression)"
    }

    data class Application(
            val function: Lambda,
            val arguments: List<Lambda>
    ) : Lambda() {
        override fun getBoundVariables() = function.getBoundVariables() +
                arguments.flatMap { it.getBoundVariables() }

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }
}


