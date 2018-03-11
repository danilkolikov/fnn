/**
 * Inference algorithm for algebraic types.
 * Infers types of constructors and extracts definitions
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.*
import thesis.preprocess.types.*

interface AlgebraicTypeInferenceContext : TypeInferenceContext {

    val typeDefinitions: MutableMap<TypeName, TypeExpression>

}

class AlgebraicTypeInferenceAlgorithm : TypeInferenceAlgorithm<AlgebraicTypeInferenceContext, TypeExpression>() {

    override fun Definition<TypeExpression>.getLocalContext(context: AlgebraicTypeInferenceContext): TypeInferenceLocalContext<AlgebraicTypeInferenceContext, TypeExpression> {
        val definedTypes = context.typeScope.keys + setOf(name)
        val definedExpressions = context.expressionScope.keys
        val scope = context.typeScope + (expression.getConstructors() - definedTypes)
                        .map { it to VariableTerm(context.nameGenerator.next(it)) }
                        .toMap()
        return TypeInferenceLocalContext(
                context,
                this,
                emptyMap(),
                definedTypes,
                definedExpressions,
                scope
        )
    }

    override fun TypeInferenceLocalContext<AlgebraicTypeInferenceContext, TypeExpression>.updateGlobalContext(): AlgebraicTypeInferenceContext {
        globalContext.typeDefinitions[definition.name] = definition.expression
        globalContext.typeScope[definition.name] = VariableTerm(definition.name)

        val constructors = definition.expression.getConstructors()
        constructors.forEach {
            val term = scope[it] ?: throw TypeInferenceError(definition.expression, globalContext)
            val type = solution[term] ?: throw TypeInferenceError(definition.expression, globalContext)
            globalContext.expressionScope[it] = type
        }
        return globalContext
    }

    private fun TypeExpression.getConstructors(): Set<String> = when (this) {
        is TypeLiteral -> setOf(name)
        is TypeProduct -> setOf(name)
        is TypeSum -> operands.flatMap { it.getConstructors() }.toSet()
    }

    override fun TypeExpression.getEquations(
            localContext: TypeInferenceLocalContext<AlgebraicTypeInferenceContext, TypeExpression>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is TypeLiteral -> {
            val resultType = localContext.scope[name]
                    ?: throw UnknownTypeError(name, localContext.globalContext)
            resultType to listOf()
        }
        is TypeSum -> {
            val resultType = VariableTerm(localContext.globalContext.nameGenerator.next("t"))
            val operands = operands.map { it.getEquations(localContext) }
            val resultNames = operands.map { it.first }
            val equations = operands.flatMap { it.second }
            resultType to equations + resultNames.map {
                AlgebraicEquation(
                        it,
                        resultType
                )
            }
        }
        is TypeProduct -> {
            val productResult = VariableTerm(localContext.globalContext.nameGenerator.next("t"))
            val constructorType = localContext.scope[name]
                    ?: throw UnknownTypeError(name, localContext.globalContext)

            val operands = operands.map { it.getEquations(localContext) }
            val resultNames = operands.map { it.first }
            val equations = operands.flatMap { it.second }
            val term = resultNames.foldRight<AlgebraicTerm, AlgebraicTerm>(productResult, { typeName, res ->
                FunctionTerm(
                        FUNCTION_SIGN,
                        listOf(typeName, res)
                )
            })
            productResult to equations + listOf(AlgebraicEquation(
                    constructorType,
                    term
            ))
        }
    }
}