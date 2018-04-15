/**
 * Different results of processing of lambda programm
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.algebraic.type.RawAlgebraicType
import thesis.preprocess.expressions.lambda.LambdaWithPatterns
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.lambda.untyped.UntypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.spec.parametrised.ParametrisedSpec
import thesis.preprocess.spec.parametrised.ParametrisedTrainableSpec
import thesis.preprocess.spec.Spec
import thesis.preprocess.spec.TypeSpec

typealias UntypedLambdaWithPatterns = LambdaWithPatterns<UntypedLambda, UntypedPattern>

data class SortedExpressions(
        val typeDefinitions: LinkedHashMap<TypeName, RawAlgebraicType>,
        val typeDeclarations: LinkedHashMap<LambdaName, Parametrised<RawType>>,
        val lambdaDefinitions: LinkedHashMap<LambdaName, List<UntypedLambdaWithPatterns>>
)

typealias TypedLambdaWithPatterns = LambdaWithPatterns<TypedLambda<Type>, TypedPattern<Type>>

data class InferredLambda(
        val type: Parametrised<Type>,
        val expressions: List<TypedLambdaWithPatterns>
)

data class InferredExpressions(
        val typeDefinitions: LinkedHashMap<TypeName, AlgebraicType>,
        val lambdaDefinitions: LinkedHashMap<LambdaName, InferredLambda>
)

typealias InstanceSignature = List<LambdaName>

typealias TypeSignature = List<RawType>

data class ParametrisedSpecs(
        val typeDefinitions: LinkedHashMap<TypeName, AlgebraicType>,
        val typeInstances: Instances<AlgebraicType>,
        val instances: Instances<ParametrisedSpec>,
        val trainable: LinkedHashMap<InstanceSignature, List<ParametrisedTrainableSpec>>
)

data class Specs(
        val typeSpecs: Instances<TypeSpec>,
        val instances: Instances<Spec>,
        val parametrisedInstances: Instances<ParametrisedSpec>,
        val trainable: LinkedHashMap<InstanceSignature, List<ParametrisedTrainableSpec>>
)