package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.results.InstanceName
import thesis.preprocess.results.TypeSig

/**
 * Polymorphic objects can be either base objects or instantiated ones
 *
 * @author Danil Kolikov
 */
sealed class Polymorphic<T> {

    abstract val name: InstanceName

    abstract val item: T

    data class Base<T>(
            override val item: T,
            override val name: InstanceName
    ): Polymorphic<T>()

    data class Instance<T>(
            override val item: T,
            val base: Polymorphic<T>,
            override val name: InstanceName,
            val parameters: Map<TypeVariableName, TypeSig>
    ): Polymorphic<T>()
}