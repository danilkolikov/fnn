package thesis.preprocess.expressions.lambda.untyped

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.lambda.Lambda
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.raw.RawType

/**
 * AST of untyped lambda-expression
 *
 * @author Danil Kolikov
 */
sealed class UntypedLambda : Lambda {

    data class Literal(
            override val name: LambdaName
    ) : UntypedLambda(), Lambda.Literal {

        override fun toString() = name
    }

    data class TypedExpression(
            val expression: UntypedLambda,
            val type: Parametrised<RawType>
    ) : UntypedLambda() {

        override fun toString() = "($expression : $type)"
    }

    data class Trainable(
            override val options: Map<String, Any>
    ) : UntypedLambda(), Lambda.Trainable {

        override fun toString(): String {
            return if (options.isEmpty()) "@learn" else
            "@learn { ${options.entries.joinToString(", ") { "${it.key}: ${it.value}" }} }"
        }
    }

    data class Abstraction(
            override val arguments: List<UntypedLambda.Literal>,
            override val expression: UntypedLambda
    ) : UntypedLambda(), Lambda.Abstraction<UntypedLambda> {

        override fun toString() = "(\\${arguments.joinToString(" ")}. $expression)"
    }

    data class LetAbstraction(
            override val bindings: List<Binding>,
            override val expression: UntypedLambda
    ) : UntypedLambda(), Lambda.LetAbstraction<UntypedLambda> {

        override fun toString() = "@let ${bindings.joinToString(", ")} @in $expression"

        data class Binding(
                override val name: LambdaName,
                override val expression: UntypedLambda
        ) : Lambda.LetAbstraction.Binding<UntypedLambda> {

            override fun toString() = "$name = $expression"
        }
    }

    data class RecAbstraction(
            override val argument: UntypedLambda.Literal,
            override val expression: UntypedLambda
    ) : UntypedLambda(), Lambda.RecAbstraction<UntypedLambda> {

        override fun toString() = "(@rec ${argument.name} @in $expression)"
    }

    data class Application(
            override val function: UntypedLambda,
            override val arguments: List<UntypedLambda>
    ) : UntypedLambda(), Lambda.Application<UntypedLambda> {

        override fun toString() = "($function ${arguments.joinToString(" ")})"
    }

    data class CaseAbtraction(
            override val expression: UntypedLambda,
            override val cases: List<Case>
    ): UntypedLambda(), Lambda.CaseAbstraction<UntypedLambda> {

        override fun toString() = "(@case $expression @of ${cases.joinToString(", ")})"

        data class Case(
                override val pattern: UntypedPattern,
                override val expression: UntypedLambda
        ): Lambda.CaseAbstraction.Case<UntypedLambda> {
            override fun toString() = "$pattern -> $expression"
        }
    }
}


