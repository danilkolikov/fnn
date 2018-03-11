/**
 * Inference algorithm for algebraic types.
 * Infers types of constructors and extracts definitions
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.*
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

interface AlgebraicTypeInferenceContext : TypeInferenceContext {

    val typeDefinitions: MutableMap<TypeName, AlgebraicType>

}

class AlgebraicTypeInferenceAlgorithm : TypeInferenceAlgorithm<AlgebraicTypeInferenceContext, AlgebraicType>() {

    override fun Definition<AlgebraicType>.getLocalContext(context: AlgebraicTypeInferenceContext): TypeInferenceLocalContext<AlgebraicTypeInferenceContext, AlgebraicType> {
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

    override fun TypeInferenceLocalContext<AlgebraicTypeInferenceContext, AlgebraicType>.updateGlobalContext(): AlgebraicTypeInferenceContext {
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

    private fun AlgebraicType.getConstructors(): Set<String> = when (this) {
        is AlgebraicType.Literal -> setOf(name)
        is AlgebraicType.Product -> setOf(name)
        is AlgebraicType.Sum -> operands.flatMap { it.getConstructors() }.toSet()
    }

    override fun AlgebraicType.getEquations(
            localContext: TypeInferenceLocalContext<AlgebraicTypeInferenceContext, AlgebraicType>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is AlgebraicType.Literal -> {
            val resultType = localContext.scope[name]
                    ?: throw UnknownTypeError(name, localContext.globalContext)
            resultType to listOf()
        }
        is AlgebraicType.Sum -> {
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
        is AlgebraicType.Product -> {
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