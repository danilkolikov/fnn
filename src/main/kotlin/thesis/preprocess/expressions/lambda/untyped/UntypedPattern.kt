package thesis.preprocess.expressions.lambda.untyped

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.lambda.Pattern

/**
 * AST of pattern for pattern-matching
 *
 * @author Danil Kolikov
 */
sealed class UntypedPattern : Pattern {

    data class Variable(
            override val name: LambdaName
    ) : UntypedPattern(), Pattern.Variable {
        override fun getVariables() = setOf(name)

        override fun toString() = name
    }

    data class Object(
            override val name: TypeName
    ) : UntypedPattern(), Pattern.Object {

        override fun getVariables() = emptySet<TypeName>()

        override fun toString() = name
    }

    data class Constructor(
            override val name: TypeName,
            override val arguments: List<UntypedPattern>
    ) : UntypedPattern(), Pattern.Constructor<UntypedPattern> {

        override fun getVariables() = arguments.flatMap { it.getVariables() }.toSet()

        override fun toString() = "($name ${arguments.joinToString(" ")})"
    }
}