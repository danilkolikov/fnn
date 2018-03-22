package thesis.preprocess.renaming

import thesis.preprocess.Processor
import thesis.preprocess.results.RenamedType
import thesis.preprocess.results.RenamedTypeImpl
import thesis.preprocess.results.SimpleType

/**
 * Renames bound variables in type definitions
 *
 * @author Danil Kolikov
 */
class AlgebraicTypeRenamingProcessor(
        private val nameGenerator: NameGenerator
) : Processor<SimpleType, RenamedType> {
    override fun process(data: SimpleType): RenamedType {
        // Now types don't have bound variables to rename
        return RenamedTypeImpl(data, emptyMap())
    }
}