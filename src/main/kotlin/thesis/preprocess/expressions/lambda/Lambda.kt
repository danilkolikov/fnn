package thesis.preprocess.expressions.lambda

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.LambdaName

/**
 * Interface for lambda expressions
 *
 * @author Danil Kolikov
 */
interface Lambda : Expression {

    interface Literal : Lambda {
        val name: String
    }

    interface Trainable : Lambda {
        val options: Map<String, Any>
    }

    interface Abstraction<out E: Lambda> : Lambda {
        val arguments: List<Lambda.Literal>
        val expression: E
    }

    interface LetAbstraction<out E: Lambda> : Lambda {
        val bindings: List<Binding<*>>
        val expression: E

        interface Binding<out E: Lambda> {
            val name: LambdaName
            val expression: E
        }
    }

    interface RecAbstraction<out E: Lambda> : Lambda {
        val argument: Lambda.Literal
        val expression: E
    }

    interface Application<out E: Lambda> : Lambda {
        val function: E
        val arguments: List<E>
    }
}