package thesis.preprocess.expressions

/**
 * Algebraic type - can be either a sum, product of types or just a type literal
 */
sealed class AlgebraicType : Expression, Replaceable<AlgebraicType> {

    abstract fun getConstructors(): Set<TypeName>

    data class Literal(val name: TypeName) : AlgebraicType() {
        override fun getConstructors() = setOf(name)

        override fun replace(map: Map<String, AlgebraicType>) = map[name] ?: this
    }

    data class Sum(
            val operands: List<AlgebraicType>
    ) : AlgebraicType() {
        override fun getConstructors() = operands.flatMap { it.getConstructors() }.toSet()

        override fun replace(map: Map<String, AlgebraicType>) = Sum(operands.map { it.replace(map) })
    }

    data class Product(
            val name: String,
            val operands: List<AlgebraicType>
    ) : AlgebraicType() {
        override fun getConstructors() = setOf(name)

        override fun replace(map: Map<String, AlgebraicType>) = Product(name, operands.map { it.replace(map) })
    }
}