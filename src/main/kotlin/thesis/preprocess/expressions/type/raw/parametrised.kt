/**
 * Extensions for Parametrised<RawType>
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.expressions.type.raw

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.type.Parametrised
import thesis.utils.NameGenerator

fun Parametrised<RawType>.instantiateVariables(
        nameGenerator: NameGenerator
): Pair<Parametrised<RawType>, Map<TypeVariableName, RawType>> {
    val variables = parameters.map { it to RawType.Literal(nameGenerator.next(it)) }.toMap()
    return type.replace(variables).bindVariables() to variables
}

fun Parametrised<RawType>.bindLiterals(definedLiterals: Set<String>): Parametrised<RawType> =
        type.bindLiterals(definedLiterals).bindVariables()