/**
 * Extensions for Parametrised<RawType>
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.expressions.type.raw

import thesis.preprocess.expressions.type.Parametrised
import thesis.utils.NameGenerator

fun Parametrised<RawType>.instantiateVariables(
        nameGenerator: NameGenerator
): Parametrised<RawType> {
    val variables = parameters.map { it to RawType.Literal(nameGenerator.next(it)) }.toMap(LinkedHashMap())
    val bound = type.replace(variables)
    return Parametrised(
            emptyList(),
            bound,
            variables,
            initialParameters
    )
}