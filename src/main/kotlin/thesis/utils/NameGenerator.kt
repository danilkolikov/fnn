package thesis.utils

/**
 * Generates unique names for types and variables
 */
class NameGenerator(private val namePrefix: String) {
    private val counters = mutableMapOf<String, Int>()

    constructor() : this(NAME_PREFIX)

    fun next(prefix: String): String {
        counters.putIfAbsent(prefix, 0)
        return namePrefix + prefix + counters.compute(prefix, { _, value -> value!! + 1})
    }

    companion object {
        const val NAME_PREFIX = ""
    }
}