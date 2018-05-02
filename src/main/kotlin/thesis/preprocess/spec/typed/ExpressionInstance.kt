package thesis.preprocess.spec.typed

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.results.InstanceName

/**
 * Specification of an instance of a polymorphic expression
 *
 * @author Danil Kolikov
 */
data class ExpressionInstance(
        val spec: TypedSpec,
        val name: InstanceName,
        val polymorphicName: InstanceName,
        val parameters: Map<TypeVariableName, InstanceName>
)