package thesis.preprocess

/**
 * Abstract processor of data. Basically, it's a function
 *
 * @author Danil Kolikov
 */
@FunctionalInterface
interface Processor<in T, out S> {
    fun process(data: T): S
}