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

/**
 * Result of parsing of lambda program - three groups of expressions
 *
 * @author Danil Kolikov
 */
data class SortedExpressions(
        val typeDefinitions: LinkedHashMap<TypeName, RawAlgebraicType>,
        val typeDeclarations: LinkedHashMap<LambdaName, Parametrised<RawType>>,
        val lambdaDefinitions: LinkedHashMap<LambdaName, List<UntypedLambdaWithPatterns>>
)

typealias TypedLambdaWithPatterns = LambdaWithPatterns<TypedLambda<Type>, TypedPattern<Type>>


/**
 * Result of type inference of single lambda expression
 *
 * @author Danil Kolikov
 */
data class InferredLambda(
        val type: Parametrised<Type>,
        val expressions: List<TypedLambdaWithPatterns>,
        val isRecursive: Boolean
)


/**
 * Result of type inference of lambda program. Every type has information about it's constructors, every
 * lambda has inferred type
 *
 * @author Danil Kolikov
 */
data class InferredExpressions(
        val typeDefinitions: LinkedHashMap<TypeName, AlgebraicType>,
        val lambdaDefinitions: LinkedHashMap<LambdaName, InferredLambda>
)

typealias InstanceSignature = List<LambdaName>

typealias TypeSignature = List<RawType>

/**
 * Specification of program. Basically, it's the set of instances of lambda expressions, types and
 * trainable expressions. Every instance has information about type parameters used for instantiation
 *
 * @author Danil Kolikov
 */
data class ParametrisedSpecs(
        val typeDefinitions: LinkedHashMap<TypeName, AlgebraicType>,
        val typeInstances: Instances<AlgebraicType>,
        val instances: Instances<ParametrisedSpec>,
        val trainable: LinkedHashMap<InstanceSignature, List<ParametrisedTrainableSpec>>
)

/**
 * Specification of executable program. For every expression there is a resolved information about stack,
 * variables point on a specific place on stack
 *
 * @author Danil Kolikov
 */
data class Specs(
        val typeSpecs: Instances<TypeSpec>,
        val instances: Instances<Spec>,
        val parametrisedInstances: Instances<ParametrisedSpec>,
        val trainable: LinkedHashMap<InstanceSignature, List<ParametrisedTrainableSpec>>
)