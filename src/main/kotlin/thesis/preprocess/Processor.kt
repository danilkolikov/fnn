package thesis.preprocess

/**
 * Abstract processor of data. Basically, it's a function that can do some side effects
 *
 * @author Danil Kolikov
 */
@FunctionalInterface
interface Processor<in T, out S> {

    fun process(data: T): S
}