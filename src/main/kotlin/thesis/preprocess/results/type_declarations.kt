/**
 * Representation of type declarations
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type

interface SimpleTypeDeclaration {
    val name: LambdaName
    val type: Type
}

interface RenamedTypeDeclaration : SimpleTypeDeclaration {
    val nameMap: Map<String, Type>
}

// Implementations

data class SimpleTypeDeclarationImpl(
        override val name: LambdaName,
        override val type: Type
) : SimpleTypeDeclaration

data class RenamedTypeDeclarationImpl(
        private val _type: SimpleTypeDeclaration,
        override val nameMap: Map<String, Type>
) : RenamedTypeDeclaration, SimpleTypeDeclaration by _type