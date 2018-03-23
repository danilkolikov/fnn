package thesis.preprocess.expressions

/**
 * Pattern for pattern-matching
 *
 * @author Danil Kolikov
 */
sealed class Pattern {

    abstract fun getVariables(): Set<LambdaName>

    data class Variable(
            val name: LambdaName
    ) : Pattern() {
        override fun getVariables() = setOf(name)

        override fun toString() = name
    }

    data class Object(
            val name: TypeName
    ) : Pattern() {

        override fun getVariables() = emptySet<LambdaName>()

        override fun toString() = name
    }

    data class Constructor(
            val name: TypeName,
            val arguments: List<Pattern>
    ) : Pattern() {

        override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

        override fun toString() = "($name ${arguments.joinToString(" ")})"
    }
}