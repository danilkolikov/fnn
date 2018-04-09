package thesis.preprocess.expressions.lambda.untyped

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.lambda.Lambda
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.raw.RawType

/**
 * Untyped Lambda-expression
 */
sealed class UntypedLambda : Lambda {

    abstract fun getBoundVariables(): Set<String>

    data class Literal(
            override val name: LambdaName
    ) : UntypedLambda(), Lambda.Literal {
        override fun getBoundVariables() = emptySet<String>()

        override fun toString() = name
    }

    data class TypedExpression(
            val expression: UntypedLambda,
            val type: Parametrised<RawType>
    ) : UntypedLambda() {
        override fun getBoundVariables() = expression.getBoundVariables()

        override fun toString() = "($expression : $type)"
    }

    class Trainable : UntypedLambda(), Lambda.Trainable {
        override fun getBoundVariables() = emptySet<String>()

        override fun toString() = "@learn"
    }

    data class Abstraction(
            override val arguments: List<UntypedLambda.Literal>,
            override val expression: UntypedLambda
    ) : UntypedLambda(), Lambda.Abstraction<UntypedLambda> {
        override fun getBoundVariables() = arguments.toSet().map { it.name }.toSet() +
                expression.getBoundVariables()

        override fun toString() = "(\\${arguments.joinToString(" ")}. $expression)"
    }

    data class LetAbstraction(
            override val bindings: List<Binding>,
            override val expression: UntypedLambda
    ) : UntypedLambda(), Lambda.LetAbstraction<UntypedLambda> {

        override fun getBoundVariables() = expression.getBoundVariables() +
                bindings.flatMap { it.getBoundVariables() }.toSet()

        override fun toString() = "@let ${bindings.joinToString(", ")} @in $expression"

        data class Binding(
                override val name: LambdaName,
                override val expression: UntypedLambda
        ) : Lambda.LetAbstraction.Binding<UntypedLambda> {
            fun getBoundVariables() = setOf(name) + expression.getBoundVariables()

            override fun toString() = "$name = $expression"
        }
    }

    data class Application(
            override val function: UntypedLambda,
            override val arguments: List<UntypedLambda>
    ) : UntypedLambda(), Lambda.Application<UntypedLambda> {
        override fun getBoundVariables() = function.getBoundVariables() +
                arguments.flatMap { it.getBoundVariables() }

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }
}


