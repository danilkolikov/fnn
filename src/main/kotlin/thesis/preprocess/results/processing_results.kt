/**
 * Different results of processing of lambda programm
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName

data class SortedExpressions(
        val typeDefinitions: Map<TypeName, SimpleType>,
        val typeDeclarations: Map<LambdaName, Type>,
        val lambdaDefinitions: Map<LambdaName, SimpleLambda>
)

data class RenamedExpressions(
        val typeDefinitions: Map<TypeName, RenamedType>,
        val typeDeclarations: Map<LambdaName, Type>,
        val lambdaDefinitions: Map<LambdaName, RenamedLambda>
)

data class InferredExpressions(
        val typeDefinitions: Map<TypeName, InferredType>,
        val lambdaDefinitions: Map<LambdaName, InferredLambda>
)

data class InMemoryExpressions(
        val typeDefinitions: Map<TypeName, InMemoryType>,
        val lambdaDefinitions: Map<LambdaName, InferredLambda>
)
