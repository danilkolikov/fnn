/**
 * Exceptions of type inference
 */
package thesis.preprocess.types

import thesis.preprocess.expressions.Expression
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.type.Type

class TypeInferenceError(expression: Expression) : Exception(
        "Can't infer type of $expression"
)

class TypeInferenceErrorWithoutContext : Exception(
        "Can't infer type"
)

class UnsupportedTrainableType(type: Type) : Exception(
        "Type of @learn in expression is unsupported: " +
                "expected functions from Algebraic types to Algebraic types, but got $type"
)

class UnknownTypeError(name: String) : Exception("Type $name is not defined") {

    constructor(names: Set<TypeName>) : this(
            names.toString()
    )
}

class UnknownExpressionError(name: LambdaName) : Exception(
        "Expression with name $name is not defined"
)