package thesis.preprocess.ast

import thesis.preprocess.Processor
import thesis.preprocess.results.SimpleLambdaImpl
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
                    .map { it.name to SimpleTypeImpl(it.expression) }
                    .toMap(LinkedHashMap()),
            data.expressions.filterIsInstance(LambdaTypeDeclaration::class.java)
                    .map { it.name to it.type }
                    .toMap(LinkedHashMap()),
            data.expressions.filterIsInstance(LambdaDefinition::class.java)
                    .groupByTo(LinkedHashMap(), { it.name })
                    .mapValuesTo(LinkedHashMap(), { SimpleLambdaImpl(it.value.map { it.expression }) })
    )

}