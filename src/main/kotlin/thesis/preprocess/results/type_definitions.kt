/**
 * Representations of type definitions
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.memory.TypeMemoryInformation

interface SimpleType {
    val type: AlgebraicType
}

interface RenamedType : SimpleType {
    val nameMap: Map<String, AlgebraicType>
}

interface InferredType : RenamedType {
    val constructors: Map<TypeName, Type>
}

interface InMemoryType : InferredType {
    val memoryInfo: TypeMemoryInformation
}

// Implementations

data class SimpleTypeImpl(
        override val type: AlgebraicType
) : SimpleType

data class RenamedTypeImpl(
        private val _type: SimpleType,
        override val nameMap: Map<String, AlgebraicType>
) : RenamedType, SimpleType by _type

data class InferredTypeImpl(
        private val _type: RenamedType,
        override val constructors: Map<TypeName, Type>
) : InferredType, RenamedType by _type

data class InMemoryTypeImpl(
        private val _type: InferredType,
        override val memoryInfo: TypeMemoryInformation
) : InMemoryType, InferredType by _type