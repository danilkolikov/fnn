package thesis.preprocess.ast

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaWithPatterns
import thesis.preprocess.results.SimpleLambdaImpl
import thesis.preprocess.results.SimpleTypeDeclarationImpl
import thesis.preprocess.results.SimpleTypeImpl
import thesis.preprocess.results.SortedExpressions

/**
 * Separates expressions of lambda program to 3 categories
 *
 * @author Danil Kolikov
 */
class ExpressionSorter : Processor<LambdaProgram, SortedExpressions> {

    override fun process(data: LambdaProgram) = SortedExpressions(
            data.expressions.filterIsInstance(TypeDefinition::class.java)
                    .map { SimpleTypeImpl(it.name, it.expression) },
            data.expressions.filterIsInstance(LambdaTypeDeclaration::class.java)
                    .map { SimpleTypeDeclarationImpl(it.name, it.type) },
            data.expressions.filterIsInstance(LambdaDefinition::class.java)
                    .groupByTo(LinkedHashMap(), { it.name })
                    .map { (name, list) ->
                        SimpleLambdaImpl(
                                name,
                                list.map { LambdaWithPatterns(it.patterns, it.expression) }
                        )
                    }
    )
}