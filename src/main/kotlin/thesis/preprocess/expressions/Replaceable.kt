package thesis.preprocess.expressions

/**
 * Interface for expressions that allows replacement of variables to expressions
 */
interface Replaceable<T> {

    fun replace(map: Map<String, T>): T
}