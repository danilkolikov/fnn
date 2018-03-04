package thesis.preprocess.types

class NameGenerator(private val prefix: String) {
    private var counter = 0

    fun next() = prefix + counter++
}