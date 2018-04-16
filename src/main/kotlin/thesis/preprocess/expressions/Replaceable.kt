package thesis.preprocess.expressions

/**
 * Interface for expressions that allow replacement of variables to expressions
 *
 * @author Danil Kolikov
 */
interface Replaceable<T> {

    fun replace(map: Map<String, T>): T
}