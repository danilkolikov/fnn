package thesis.preprocess.renaming

/**
 * Generates unique names for types and variables
 */
class NameGenerator {
    private val counters = mutableMapOf<String, Int>()

    fun next(prefix: String): String {
        counters.putIfAbsent(prefix, 0)
        return NAME_PREFIX + prefix + counters.compute(prefix, { _, value -> value!! + 1})
    }

    companion object {
        const val NAME_PREFIX = "."
    }
}