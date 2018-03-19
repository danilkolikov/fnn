/**
 * Global type contexts
 */
package thesis.preprocess

import thesis.preprocess.expressions.*
import thesis.preprocess.typeinfo.TypeInformation
import thesis.preprocess.types.NameGenerator
import thesis.preprocess.types.toSimpleType
import thesis.utils.AlgebraicTerm

/**
 * Base type context - has only name generator
 */
interface TypeContext {
    val nameGenerator: NameGenerator
}

/**
 * Context used by inference algorithm for algebraic types
 */
interface AlgebraicTypeContext : TypeContext {

    val typeDefinitions: MutableMap<TypeName, AlgebraicType>

    val typeConstructors: MutableMap<TypeName, AlgebraicTerm>

    val typeScope: MutableMap<TypeName, AlgebraicTerm>

    val typeInformation: MutableMap<TypeName, TypeInformation>
}

/**
 * Context used by inference algorithm for lambda terms
 */
interface LambdaTermsTypeContext : AlgebraicTypeContext {

    val lambdaDefinitions: MutableMap<LambdaName, Lambda>

    val expressionScope: MutableMap<LambdaName, AlgebraicTerm>
}

/**
 * Implementation of global context
 */
class Context(
        override val typeDefinitions: MutableMap<TypeName, AlgebraicType> = mutableMapOf(),
        override val lambdaDefinitions: MutableMap<LambdaName, Lambda> = mutableMapOf(),

        override val typeConstructors: MutableMap<TypeName, AlgebraicTerm> = mutableMapOf(),
        override val typeScope: MutableMap<TypeName, AlgebraicTerm> = mutableMapOf(),
        override val expressionScope: MutableMap<String, AlgebraicTerm> = mutableMapOf(),
        override val typeInformation: MutableMap<TypeName, TypeInformation> = mutableMapOf(),

        override val nameGenerator: NameGenerator = NameGenerator()
) : AlgebraicTypeContext, LambdaTermsTypeContext {
    val types: Map<LambdaName, Type>
        get() = (typeConstructors + expressionScope)
                .map { (type, value) -> type to value.toSimpleType() }
                .toMap()

}