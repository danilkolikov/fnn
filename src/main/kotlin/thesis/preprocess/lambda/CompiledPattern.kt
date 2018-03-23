package thesis.preprocess.lambda

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.memory.TypeMemoryInformation

/**
 * Executable patterns. Can match object and return sub-objects
 *
 * @author Danil Kolikov
 */
sealed class CompiledPattern {

    abstract fun match(compiledLambda: CompiledLambda): Map<LambdaName, CompiledLambda>?

    data class Variable(
            val name: LambdaName
    ) : CompiledPattern() {
        override fun match(compiledLambda: CompiledLambda) = mapOf(name to compiledLambda)
    }

    data class Object(
            val information: TypeMemoryInformation.ConstructorInformation
    ) : CompiledPattern() {
        override fun match(compiledLambda: CompiledLambda): Map<LambdaName, CompiledLambda>? {
            if (compiledLambda !is CompiledLambda.Object) {
                // Can't match
                return null
            }
            // Only 1 element of data on appropriate position should be equal to 1
            val position = information.offset
            val correct = compiledLambda.data
                    .filterIndexed { index, sh -> (index == position) == (sh == 1.toShort()) }
                    .count()
            return if (correct != compiledLambda.data.size) null else emptyMap()
        }
    }

    data class Constructor(
            val arguments: List<CompiledPattern>,
            val information: TypeMemoryInformation.ConstructorInformation
    ) : CompiledPattern() {
        override fun match(compiledLambda: CompiledLambda): Map<LambdaName, CompiledLambda>? {
            if (compiledLambda !is CompiledLambda.Object) {
                // Can't match
                return null
            }
            val objects = information.argumentOffsets.map { (type, size, offset) ->
                val data = Array(size, { 0.toShort() })
                System.arraycopy(compiledLambda.data, information.offset + offset, data, 0, size)
                CompiledLambda.Object(type, data)
            }
            val matchedArguments = arguments.zip(objects) { pattern, obj -> pattern.match(obj) }
            return if (matchedArguments.any { it == null }) null else
                matchedArguments.filterNotNull().flatMap { it.entries.map { it.toPair() } }.toMap()
        }
    }
}