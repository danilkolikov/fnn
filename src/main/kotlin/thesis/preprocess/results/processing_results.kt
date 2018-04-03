/**
 * Different results of processing of lambda programm
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.spec.Spec
import thesis.preprocess.spec.TypeSpec

data class SortedExpressions(
        val typeDefinitions: List<SimpleType>,
        val typeDeclarations: List<SimpleTypeDeclaration>,
        val lambdaDefinitions: List<SimpleLambda>
)

data class RenamedExpressions(
        val typeDefinitions: List<RenamedType>,
        val typeDeclarations: List<RenamedTypeDeclaration>,
        val lambdaDefinitions: List<RenamedLambda>
)

data class InferredExpressions(
        val typeDefinitions: List<InferredType>,
        val lambdaDefinitions: List<InferredLambda>
)

data class InMemoryExpressions(
        val typeDefinitions: List<InMemoryType>,
        val lambdaDefinitions: List<InferredLambda>
)

data class Specs(
        val typeSpecs: List<Pair<TypeName, TypeSpec>>,
        val specs: List<Spec.Function.Guarded>
)