package thesis.preprocess.expressions.type.raw

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.term.AlgebraicTerm
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Implication
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.types.UnexpectedTypeParametersAmount
import thesis.preprocess.types.UnknownTypeError

/**
 * A simple type - either literal or function
 */
sealed class RawType : Expression, Implication<RawType> {

    abstract fun toType(typeDefinitions: Map<TypeName, AlgebraicType>): Type

    abstract fun toAlgebraicTerm(): AlgebraicTerm

    fun bindVariables(alreadyBound: List<TypeVariableName>): Parametrised<RawType> {
        val variables = getVariables() - alreadyBound
        val newVariables = alreadyBound + variables.sorted()
        return Parametrised(
                newVariables,
                this,
                newVariables.map { it to RawType.Variable(it) }.toMap()
        )
    }

    abstract fun bindLiterals(definedLiterals: Set<TypeName>): RawType

    /**
     * Checks that all type applications have correct kinds
     */
    abstract fun checkWellKinded(typeDefinitions: Map<TypeName, AlgebraicType>)

    data class Literal(val name: TypeName) : RawType() {

        override fun toString() = name

        override fun getVariables() = emptySet<TypeVariableName>()

        override fun getOperands() = listOf(this)

        override fun replace(map: Map<String, RawType>) = this  // Literals cannot be replaced

        override fun replaceLiterals(map: Map<TypeName, RawType>) = map[name] ?: this

        override fun toType(typeDefinitions: Map<TypeName, AlgebraicType>) =
                typeDefinitions[name]?.let { Type.Application(it, emptyList()) } ?: Type.Variable(name)

        override fun toAlgebraicTerm() = AlgebraicTerm.Variable(name)

        override fun bindLiterals(definedLiterals: Set<TypeName>) =
                if (definedLiterals.contains(name)) Literal(name) else Variable(name)

        override fun checkWellKinded(typeDefinitions: Map<TypeName, AlgebraicType>) = Unit
    }

    data class Variable(val name: TypeVariableName) : RawType() {
        override fun toString() = name

        override fun getVariables() = setOf(name)

        override fun getOperands() = listOf(this)

        override fun replace(map: Map<String, RawType>) = map[name] ?: this

        override fun replaceLiterals(map: Map<TypeName, RawType>) = this // Variables aren't replaced

        override fun toType(typeDefinitions: Map<TypeName, AlgebraicType>) = Type.Variable(name)

        override fun toAlgebraicTerm() = throw IllegalStateException("Unexpected variable $name")

        override fun bindLiterals(definedLiterals: Set<TypeName>) = this

        override fun checkWellKinded(typeDefinitions: Map<TypeName, AlgebraicType>) = Unit
    }

    data class Application(val name: TypeVariableName, val args: List<RawType>) : RawType() {
        override fun toString() = "($name ${args.joinToString(" ")})"

        override fun toType(typeDefinitions: Map<TypeName, AlgebraicType>): Type {
            val type = typeDefinitions[name] ?: throw UnknownTypeError(name)
            if (type.parameters.size != args.size) {
                throw UnexpectedTypeParametersAmount(name, type.parameters.size, args.size)
            }
            return Type.Application(type, args.map { it.toType(typeDefinitions) })
        }

        override fun toAlgebraicTerm() = AlgebraicTerm.Function(name, args.map { it.toAlgebraicTerm() })

        override fun bindLiterals(definedLiterals: Set<TypeName>) = Application(
                name,
                args.map { it.bindLiterals(definedLiterals) }
        )

        override fun getVariables() = args.flatMap { it.getVariables() }.toSet()

        override fun replaceLiterals(map: Map<TypeName, RawType>) = Application(
                name,
                args.map { it.replaceLiterals(map) }
        )

        override fun getOperands() = listOf(this)

        override fun replace(map: Map<String, RawType>) = Application(
                name,
                args.map { it.replace(map) }
        )

        override fun checkWellKinded(typeDefinitions: Map<TypeName, AlgebraicType>) {
            val type = typeDefinitions[name] ?: throw UnknownTypeError(name)
            if (args.size != type.parameters.size) {
                throw UnexpectedTypeParametersAmount(name, type.parameters.size, args.size)
            }
            args.forEach { it.checkWellKinded(typeDefinitions) }
        }
    }

    data class Function(val from: RawType, val to: RawType) : RawType() {
        override fun toString() = "($from → $to)"

        override fun getVariables() = from.getVariables() + to.getVariables()

        override fun getOperands() = listOf(from) + to.getOperands()

        override fun replace(map: Map<String, RawType>) = Function(from.replace(map), to.replace(map))

        override fun replaceLiterals(map: Map<TypeName, RawType>) = Function(
                from.replaceLiterals(map),
                to.replaceLiterals(map)
        )

        override fun toType(typeDefinitions: Map<TypeName, AlgebraicType>) = Type.Function(
                from.toType(typeDefinitions),
                to.toType(typeDefinitions)
        )

        override fun toAlgebraicTerm() = AlgebraicTerm.Function(FUNCTION_SIGN, listOf(
                from.toAlgebraicTerm(),
                to.toAlgebraicTerm()
        ))

        override fun bindLiterals(definedLiterals: Set<TypeName>) = Function(
                from.bindLiterals(definedLiterals),
                to.bindLiterals(definedLiterals)
        )

        override fun checkWellKinded(typeDefinitions: Map<TypeName, AlgebraicType>) {
            from.checkWellKinded(typeDefinitions)
            to.checkWellKinded(typeDefinitions)
        }
    }

    companion object {

        fun fromAlgebraicTerm(term: AlgebraicTerm): RawType = when (term) {
            is AlgebraicTerm.Variable -> RawType.Literal(term.name)
            is AlgebraicTerm.Function -> {
                if (term.name == FUNCTION_SIGN) {
                    // Type function
                    if (term.arguments.size != 2) {
                        throw IllegalArgumentException("Algebraic term $term doesn't represent type")
                    }
                    RawType.Function(
                            fromAlgebraicTerm(term.arguments.first()),
                            fromAlgebraicTerm(term.arguments.last())
                    )
                } else {
                    // Type application
                    RawType.Application(
                            term.name,
                            term.arguments.map { fromAlgebraicTerm(it) }
                    )
                }
            }
        }

        const val FUNCTION_SIGN = "→"
    }
}