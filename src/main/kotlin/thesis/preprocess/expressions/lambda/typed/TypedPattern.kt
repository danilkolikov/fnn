package thesis.preprocess.expressions.lambda.typed

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.Typed
import thesis.preprocess.expressions.lambda.Pattern
import thesis.preprocess.expressions.lambda.untyped.UntypedPattern
import thesis.preprocess.expressions.type.Implication
import thesis.preprocess.expressions.type.Parametrised

/**
 * Pattern for pattern-matching
 *
 *  @param T class of type
 * @author Danil Kolikov
 */
sealed class TypedPattern<T : Implication<T>> : Pattern, Typed<Parametrised<T>> {

    abstract fun <S : Implication<S>> modifyType(
            preserveParameters: Boolean = true,
            action: (T) -> S
    ): TypedPattern<S>

    fun replaceLiterals(map: Map<TypeName, T>) = modifyType { it.replaceLiterals(map) }

    fun getTypedVariables(): Map<LambdaName, Parametrised<T>> = when (this) {
        is Variable -> mapOf(name to type)
        is Object -> emptyMap()
        is Constructor -> arguments.flatMap { it.getTypedVariables().toList() }.toMap()
    }

    data class Variable<T : Implication<T>>(
            val pattern: UntypedPattern.Variable,
            override val type: Parametrised<T>
    ) : TypedPattern<T>(), Pattern.Variable by pattern {
        override fun getVariables() = setOf(name)

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Variable(
                pattern,
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "($name : $type)"
    }

    data class Object<T : Implication<T>>(
            val pattern: UntypedPattern.Object,
            override val type: Parametrised<T>
    ) : TypedPattern<T>(), Pattern.Object by pattern {

        override fun getVariables() = emptySet<TypeName>()

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Object(
                pattern,
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "($name : $type)"
    }

    data class Constructor<T : Implication<T>>(
            override val name: TypeName,
            override val arguments: List<TypedPattern<T>>,
            override val type: Parametrised<T>
    ) : TypedPattern<T>(), Pattern.Constructor<TypedPattern<T>> {

        override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

        override fun <S : Implication<S>> modifyType(
                preserveParameters: Boolean,
                action: (T) -> S
        ) = Constructor(
                name,
                arguments.map { it.modifyType(preserveParameters, action) },
                type.modifyType(preserveParameters, action)
        )

        override fun toString() = "($name ${arguments.joinToString(" ")} : $type)"
    }
}