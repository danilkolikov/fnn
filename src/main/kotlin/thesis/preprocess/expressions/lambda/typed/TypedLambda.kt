package thesis.preprocess.expressions.lambda.typed

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.Typed
import thesis.preprocess.expressions.lambda.Lambda
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.type.Implication
import thesis.preprocess.expressions.type.Parametrised

/**
 * Typed lambda-expression
 *
 * @param T Class of type
 * @author Danil Kolikov
 */
sealed class TypedLambda<T : Implication<T>> : Lambda, Typed<Parametrised<T>> {

    abstract fun <S : Implication<S>> modifyType(
            preserveParameters: Boolean = true,
            action: (T) -> S
    ): TypedLambda<S>

    fun replaceLiterals(map: Map<TypeName, T>) = modifyType { it.replaceLiterals(map) }

    data class Literal<T : Implication<T>>(
            val lambda: UntypedLambda.Literal,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Literal by lambda {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Literal(
                lambda,
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "($name : $type)"
    }

    class Trainable<T : Implication<T>>(
            val lambda: UntypedLambda.Trainable,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Trainable by lambda {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Trainable(
                lambda,
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "(@learn : $type)"
    }

    data class Abstraction<T : Implication<T>>(
            override val arguments: List<TypedLambda.Literal<T>>,
            override val expression: TypedLambda<T>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Abstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Abstraction(
                arguments.map { it.modifyType(preserveParameters, action) },
                expression.modifyType(preserveParameters, action),
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "(\\${arguments.joinToString(" ")}. $expression : $type)"
    }

    data class LetAbstraction<T : Implication<T>>(
            override val bindings: List<Binding<T>>,
            override val expression: TypedLambda<T>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.LetAbstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = LetAbstraction(
                bindings.map { it.modifyType(preserveParameters, action) },
                expression.modifyType(preserveParameters, action),
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "(@let ${bindings.joinToString(", ")} @in $expression : $type)"

        data class Binding<T : Implication<T>>(
                override val name: LambdaName,
                override val expression: TypedLambda<T>
        ) : Lambda.LetAbstraction.Binding<TypedLambda<T>> {

            fun <S : Implication<S>> modifyType(
                    preserveParameters: Boolean = true,
                    action: (T) -> S
            ): Binding<S> = Binding(
                    name,
                    expression.modifyType(preserveParameters, action)
            )

            override fun toString() = "$name = $expression"
        }
    }

    data class RecAbstraction<T : Implication<T>>(
            override val argument: TypedLambda.Literal<T>,
            override val expression: TypedLambda<T>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.RecAbstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = TypedLambda.RecAbstraction(
                argument.modifyType(preserveParameters, action),
                expression.modifyType(preserveParameters, action),
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "(@rec $argument @in $expression : $type)"
    }

    data class Application<T : Implication<T>>(
            override val function: TypedLambda<T>,
            override val arguments: List<TypedLambda<T>>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Application<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Application(
                function.modifyType(preserveParameters, action),
                arguments.map { it.modifyType(preserveParameters, action) },
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "($function ${arguments.joinToString(" ")} : $type)"
    }

    data class CaseAbstraction<T : Implication<T>>(
            override val expression: TypedLambda<T>,
            override val cases: List<Case<T>>,
            override val type: Parametrised<T>
    ): TypedLambda<T>(), Lambda.CaseAbstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = CaseAbstraction(
                expression.modifyType(preserveParameters, action),
                cases.map { it.modifyType(preserveParameters, action) },
                type.modifyType(preserveParameters, action)
        )

        data class Case<T: Implication<T>>(
                override val pattern: TypedPattern<T>,
                override val expression: TypedLambda<T>
        ): Lambda.CaseAbstraction.Case<TypedLambda<T>> {
            fun <S : Implication<S>> modifyType(
                    preserveParameters: Boolean = true,
                    action: (T) -> S
            ): Case<S> = Case(
                    pattern.modifyType(preserveParameters, action),
                    expression.modifyType(preserveParameters, action)
            )
        }
    }
}


