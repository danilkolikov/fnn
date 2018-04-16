package thesis.preprocess.ast

import thesis.preprocess.Processor
import thesis.preprocess.expressions.lambda.LambdaWithPatterns
import thesis.preprocess.results.SortedExpressions

/**
 * Separates expressions of lambda program to 3 groups - type definitions,
 * declarations of lambda expressions and lambda expressions
 *
 * @author Danil Kolikov
 */
class ExpressionSorter : Processor<LambdaProgram, SortedExpressions> {

    override fun process(data: LambdaProgram) = SortedExpressions(
            data.expressions.filterIsInstance(TypeDefinition::class.java)
                    .map { it.name to it.expression }
                    .toMap(LinkedHashMap()),
            data.expressions.filterIsInstance(LambdaTypeDeclaration::class.java)
                    .map { it.name to it.type }
                    .toMap(LinkedHashMap()),
            data.expressions.filterIsInstance(LambdaDefinition::class.java)
                    .groupByTo(LinkedHashMap(), { it.name })
                    .map { (name, list) ->
                        name to list.map { LambdaWithPatterns(it.patterns, it.expression) }
                    }
                    .toMap(LinkedHashMap())
    )
}