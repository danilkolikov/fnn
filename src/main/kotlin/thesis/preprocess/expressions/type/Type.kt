package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.raw.RawType

/**
 * A simple type - either literal or function
 */
sealed class Type : Expression, Implication<Type>, Replaceable<Type> {

    override fun bindVariables(): Parametrised<Type> {
        val variables = getVariables()
        return Parametrised(
                variables.sorted(),
                this
        )
    }

    override fun replaceLiterals(map: Map<TypeName, Type>) = this // Type has no literals

    abstract fun toRaw(withVariables: Boolean = true): RawType

    data class Algebraic(val type: AlgebraicType) : Type() {
        override fun toString() = type.name

        override fun getOperands() = listOf(this)

        override fun getVariables() = emptySet<TypeVariableName>()

        override fun replace(map: Map<String, Type>) = this // Don't replace literals

        override fun toRaw(withVariables: Boolean) = RawType.Literal(type.name)
    }

    data class Variable(val name: TypeVariableName) : Type() {
        override fun toString() = name

        override fun getOperands() = listOf(this)

        override fun getVariables() = setOf(name)

        override fun replace(map: Map<String, Type>) = map[name] ?: this

        override fun toRaw(withVariables: Boolean) = if (withVariables) RawType.Variable(name) else RawType.Literal(name)
    }

    data class Function(val from: Type, val to: Type) : Type() {
        override fun toString() = "($from → $to)"

        override fun getOperands() = listOf(this.from) + to.getOperands()

        override fun getVariables() = from.getVariables() + to.getVariables()

        override fun replace(map: Map<String, Type>) = Function(
                from.replace(map),
                to.replace(map)
        )

        override fun toRaw(withVariables: Boolean) = RawType.Function(from.toRaw(withVariables), to.toRaw(withVariables))
    }
}