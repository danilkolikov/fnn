/**
 * Inference algorithm for algebraic types.
 * Infers types of constructors and extracts definitions
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.AlgebraicTypeContext
import thesis.preprocess.ContextUpdaterByLocal
import thesis.preprocess.LocalTypeContext
import thesis.preprocess.ast.Definition
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

class AlgebraicTypeInferenceAlgorithm(
        override val context: AlgebraicTypeContext,
        override val dependent: List<ContextUpdaterByLocal<*, AlgebraicType>>
) : TypeInferenceAlgorithm<AlgebraicTypeContext, AlgebraicType>() {

    override fun AlgebraicType.rename(): Pair<AlgebraicType, Map<String, AlgebraicType>> = this to emptyMap()

    override fun Definition<AlgebraicType>.getTypeScope() = context.typeScope.keys + setOf(name)

    override fun AlgebraicType.getScope() = context.typeScope +
            getConstructors()
                    .map { it to VariableTerm(context.nameGenerator.next(it)) }
                    .toMap()

    override fun updateContext(localContext: LocalTypeContext<AlgebraicType>) {
        val name = localContext.name
        val expression = localContext.expression

        context.typeDefinitions[name] = expression
        context.typeScope[name] = VariableTerm(name)

        expression.getConstructors().forEach {
            val term = localContext.expressionScope[it] ?: throw TypeInferenceError(expression, context)
            val type = localContext.solution[term] ?: throw TypeInferenceError(expression, context)
            context.typeConstructors[it] = type
        }
    }

    override fun AlgebraicType.getEquations(
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<AlgebraicType, AlgebraicTerm>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> {
        val resultType: AlgebraicTerm
        val equations: List<AlgebraicEquation>

        when (this) {
            is AlgebraicType.Literal -> {
                resultType = scope[name]
                        ?: throw UnknownTypeError(name, context)
                equations = listOf()
            }
            is AlgebraicType.Sum -> {
                resultType = VariableTerm(context.nameGenerator.next("t"))
                val operands = operands.map { it.getEquations(scope, expressionTypes) }
                val resultNames = operands.map { it.first }
                equations = operands.flatMap { it.second } + resultNames.map {
                    AlgebraicEquation(
                            it,
                            resultType
                    )
                }
            }
            is AlgebraicType.Product -> {
                resultType = VariableTerm(context.nameGenerator.next("t"))
                val constructorType = scope[name]
                        ?: throw UnknownTypeError(name, context)

                val operands = operands.map { it.getEquations(scope, expressionTypes) }
                val resultNames = operands.map { it.first }
                val term = resultNames.foldRight<AlgebraicTerm, AlgebraicTerm>(resultType, { typeName, res ->
                    FunctionTerm(
                            FUNCTION_SIGN,
                            listOf(typeName, res)
                    )
                })
                equations = operands.flatMap { it.second } + listOf(AlgebraicEquation(
                        constructorType,
                        term
                ))
            }
        }
        expressionTypes[this] = resultType
        return resultType to equations
    }
}