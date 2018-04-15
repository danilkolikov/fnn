package thesis.preprocess.expressions.lambda.typed

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.Lambda
import thesis.preprocess.expressions.lambda.Typed
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.type.Implication
import thesis.preprocess.expressions.type.Parametrised

/**
 * Typed lambda-expression
 */
sealed class TypedLambda<T : Implication<T>> : Lambda, Typed<Parametrised<T>> {

    abstract fun <S: Implication<S>> modifyType(action: (T) -> S): TypedLambda<S>

    fun replaceLiterals(map: Map<TypeName, T>) = modifyType { it.replaceLiterals(map) }

    data class Literal<T : Implication<T>>(
            val lambda: UntypedLambda.Literal,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Literal by lambda {

        override fun <S : Implication<S>> modifyType(action: (T) -> S) = Literal(
                lambda,
                type.modifyType(action)
        )

        override fun toString() = "($name : $type)"
    }

    class Trainable<T : Implication<T>>(
            val lambda: UntypedLambda.Trainable,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Trainable by lambda {

        override fun <S : Implication<S>> modifyType(action: (T) -> S) = Trainable(
                lambda,
                type.modifyType(action)
        )

        override fun toString() = "(@learn : $type)"
    }

    data class Abstraction<T : Implication<T>>(
            override val arguments: List<TypedLambda.Literal<T>>,
            override val expression: TypedLambda<T>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Abstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(action: (T) -> S) = Abstraction(
                arguments.map { it.modifyType(action) },
                expression.modifyType(action),
                type.modifyType(action)
        )

        override fun toString() = "(\\${arguments.joinToString(" ")}. $expression : $type)"
    }

    data class LetAbstraction<T : Implication<T>>(
            override val bindings: List<Binding<T>>,
            override val expression: TypedLambda<T>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.LetAbstraction<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(action: (T) -> S) = LetAbstraction(
                bindings.map { it.modifyType(action) },
                expression.modifyType(action),
                type.modifyType(action)
        )

        override fun toString() = "(@let ${bindings.joinToString(", ")} @in $expression : $type)"

        data class Binding<T : Implication<T>>(
                override val name: LambdaName,
                override val expression: TypedLambda<T>
        ) : Lambda.LetAbstraction.Binding<TypedLambda<T>> {

            fun <S: Implication<S>> modifyType(action: (T) -> S): Binding<S> = Binding(
                    name,
                    expression.modifyType(action)
            )

            override fun toString() = "$name = $expression"
        }
    }

    data class Application<T : Implication<T>>(
            override val function: TypedLambda<T>,
            override val arguments: List<TypedLambda<T>>,
            override val type: Parametrised<T>
    ) : TypedLambda<T>(), Lambda.Application<TypedLambda<T>> {

        override fun <S : Implication<S>> modifyType(action: (T) -> S) = Application(
                function.modifyType(action),
                arguments.map { it.modifyType(action) },
                type.modifyType(action)
        )

        override fun toString() = "($function ${arguments.joinToString(" ")} : $type)"
    }
}


