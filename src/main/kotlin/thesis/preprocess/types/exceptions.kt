/**
 * Exceptions of type inference
 */
package thesis.preprocess.types

import thesis.preprocess.ast.Expression
import thesis.preprocess.ast.LambdaName
import thesis.preprocess.ast.TypeName

class TypeInferenceError(expression: Expression, context: TypeInferenceContext) : Exception(
        "Can't infer type of $expression in context $context"
)

class UnknownTypeError(message: String) : Exception(message) {
    constructor(name: TypeName, context: TypeInferenceContext) : this(
            "Type $name is not defined in context $context"
    )

    constructor(names: Set<TypeName>, context: TypeInferenceContext) : this(
            "Types $names are not defined in context $context"
    )
}

class UnknownExpressionError(name: LambdaName, context: TypeInferenceContext) : Exception(
        "Expression with name $name is not defined in context $context"
)