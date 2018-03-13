package thesis.preprocess.expressions

/**
 * Lambda-expression
 */
sealed class Lambda : Expression {

    data class Literal(val name: LambdaName) : Lambda()

    data class TypedExpression(
            val expression: Lambda,
            val type: Type
    ) : Lambda()

    object Trainable : Lambda()

    data class Abstraction(
            val arguments: List<LambdaName>,
            val expression: Lambda
    ) : Lambda()

    data class Application(
            val function: Lambda,
            val arguments: List<Lambda>
    ) : Lambda()
}


