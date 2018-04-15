package thesis.preprocess.expressions.type

/**
 * Interface for structures with variables
 *
 * @author Danil Kolikov
 */
interface WithVariables {

    fun getVariables(): Set<String>
}