package thesis.preprocess.renaming

import thesis.preprocess.Processor
import thesis.preprocess.results.RenamedExpressions
import thesis.preprocess.results.SortedExpressions

/**
 * Renames expressions of lambda program to avoid name clashing
 *
 * @author Danil Kolikov
 */
class ExpressionRenamingProcessor(
        private val nameGenerator: NameGenerator
) : Processor<SortedExpressions, RenamedExpressions> {
    override fun process(data: SortedExpressions): RenamedExpressions {
        return RenamedExpressions(
                data.typeDefinitions.map {
                    AlgebraicTypeRenamingProcessor(nameGenerator).process(it)
                },
                data.typeDeclarations.map {
                    TypeDeclarationRenamingProcessor(nameGenerator).process(it)
                },
                data.lambdaDefinitions.map {
                    LambdaRenamingProcessor(nameGenerator).process(it)
                }
        )
    }
}