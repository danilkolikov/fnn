package thesis.preprocess.expressions.lambda

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName

/**
 * Interface of pattern for pattern-matching
 *
 * @author Danil Kolikov
 */
interface Pattern {

    fun getVariables(): Set<LambdaName>

    interface Variable : Pattern {
        val name: LambdaName
    }

    interface Object : Pattern {
        val name: TypeName
    }

    interface Constructor<out P: Pattern> : Pattern {
        val name: TypeName
        val arguments: List<P>
    }
}