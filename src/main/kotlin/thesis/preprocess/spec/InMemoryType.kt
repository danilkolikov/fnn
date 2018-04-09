package thesis.preprocess.spec

/**
 * Information about memory representation of types
 *
 * @author Danil Kolikov
 */
interface InMemoryType {

    val start: Int

    val end: Int

    val size
        get() = end - start
}