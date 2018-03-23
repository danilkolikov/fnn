package thesis.preprocess.renaming

import thesis.preprocess.Processor
import thesis.preprocess.results.RenamedTypeDeclaration
import thesis.preprocess.results.RenamedTypeDeclarationImpl
import thesis.preprocess.results.SimpleTypeDeclaration

/**
 * Renames variables in type declaration of lambda expression
 *
 * @author Danil Kolikov
 */
class TypeDeclarationRenamingProcessor(
        private val nameGenerator: NameGenerator
) : Processor<SimpleTypeDeclaration, RenamedTypeDeclaration> {
    override fun process(data: SimpleTypeDeclaration): RenamedTypeDeclaration {
        // Now it shouldn't rename anything
        return RenamedTypeDeclarationImpl(data, emptyMap())
    }
}