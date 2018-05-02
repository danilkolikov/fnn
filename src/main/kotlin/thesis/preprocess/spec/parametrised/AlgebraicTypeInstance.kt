package thesis.preprocess.spec.parametrised

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.results.InstanceName

/**
 * Instance of algebraic type. Has information about the type and it's arguments
 *
 * @author Danil Kolikov
 */
data class AlgebraicTypeInstance(
        val type: AlgebraicType,
        val name: InstanceName,
        val parameters: Map<TypeVariableName, InstanceName>
)