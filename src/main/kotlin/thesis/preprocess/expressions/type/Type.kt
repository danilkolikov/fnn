package thesis.preprocess.expressions.type

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.TypeSignature

/**
 * Inferred type - has references to algebraic types
 *
 * @author Danil Kolikov
 */
sealed class Type : Expression, Implication<Type>, Replaceable<Type> {

    override fun replaceLiterals(map: Map<TypeName, Type>) = this // Type has no literals

    abstract fun toRaw(withVariables: Boolean = true): RawType

    // In the most of case instantiation is just a replacement of variables
    abstract fun instantiate(
            map: Map<TypeName, Type>
    ): Type

    data class Variable(val name: TypeVariableName) : Type() {

        override fun toString() = name

        override fun getOperands() = listOf(this)

        override fun getVariables() = setOf(name)

        override fun replace(map: Map<String, Type>) = map[name] ?: this

        override fun toRaw(withVariables: Boolean) = if (withVariables) RawType.Variable(name) else RawType.Literal(name)

        override fun instantiate(map: Map<TypeName, Type>) = replace(map)
    }

    data class Application(val type: AlgebraicType, val args: List<Type>) : Type() {

        val signature: InstanceSignature
            get() = type.signature

        val typeSignature: TypeSignature
            get() = args.map { it.toRaw() }

        override fun toString() = if (args.isEmpty()) type.name else
            "(${type.name} ${args.joinToString(" ")})"

        override fun toRaw(withVariables: Boolean) = RawType.Application(
                type.name,
                args.map { it.toRaw(withVariables) }
        )

        override fun getVariables() = args.flatMap { it.getVariables() }.toSet()

        override fun getOperands() = listOf(this)

        override fun replace(map: Map<String, Type>) = Application(
                type,
                args.map { it.replace(map) }
        )

        override fun instantiate(
                map: Map<TypeName, Type>
        ) = Application(
                type,
                args.map { it.instantiate(map) }
        )
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

        override fun instantiate(map: Map<TypeName, Type>) = Function(
                from.instantiate(map),
                to.instantiate(map)
        )
    }
}