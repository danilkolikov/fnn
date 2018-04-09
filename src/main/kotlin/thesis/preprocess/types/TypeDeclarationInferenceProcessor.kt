package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.raw.RawType

/**
 * Inferring processor for lambda type declaration.
 * Check that all types used in declaration are already defined
 *
 * @author Danil Kolikov
 */
class TypeDeclarationInferenceProcessor(
        private val typeScope: Map<TypeName, AlgebraicType>
) : Processor<LinkedHashMap<TypeName, Parametrised<RawType>>, Map<LambdaName, Parametrised<Type>>> {

    override fun process(data: LinkedHashMap<TypeName, Parametrised<RawType>>) = data.map { (key, type) ->
        key to Parametrised(
                type.parameters,
                type.type.replaceTypes(type.parameters.toSet())
        )
    }.toMap()

    private fun RawType.replaceTypes(parameters: Set<TypeVariableName>): Type = when (this) {
        is RawType.Literal -> Type.Algebraic(typeScope[name] ?: throw UnknownTypeError(name))
        is RawType.Variable -> if (parameters.contains(name)) Type.Variable(name) else throw UnknownExpressionError(name)
        is RawType.Function -> Type.Function(from.replaceTypes(parameters), to.replaceTypes(parameters))
    }
}