package thesis.preprocess.expressions.algebraic.type

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.type.Type

/**
 * Representation of algebraic type
 *
 * @author Danil Kolikov
 */
data class AlgebraicType(
        val name: TypeName,
        val structure: AlgebraicTypeStructure,
        val constructors: LinkedHashMap<TypeName, Type>
) {
}