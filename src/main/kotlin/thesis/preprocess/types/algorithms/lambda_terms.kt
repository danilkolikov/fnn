/**
 * Type inference algorithm for lambda terms
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.*
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

interface LambdaTermsTypeInferenceContext : TypeInferenceContext {

    val lambdaDefinitions: MutableMap<LambdaName, Lambda>

    val nameMap: MutableMap<LambdaName, LambdaName>
}

class LambdaTermsInferenceAlgorithm : TypeInferenceAlgorithm<LambdaTermsTypeInferenceContext, Lambda>() {

    override fun Definition<Lambda>.getLocalContext(
            context: LambdaTermsTypeInferenceContext
    ): TypeInferenceLocalContext<LambdaTermsTypeInferenceContext, Lambda> {
        val definedTypes = context.typeScope.keys
        val definedExpressions = context.expressionScope.keys + setOf(name)
        val nameMap = mutableMapOf<String, LambdaName>()
        val renamed = expression.renameArguments(emptyMap(), nameMap, context.nameGenerator)
        val scope = context.expressionScope + nameMap.keys.map { it to VariableTerm(it) }
        return TypeInferenceLocalContext(
                context,
                LambdaDefinition(name, renamed),
                nameMap,
                definedTypes,
                definedExpressions,
                scope
        )
    }

    override fun TypeInferenceLocalContext<LambdaTermsTypeInferenceContext, Lambda>.updateGlobalContext()
            : LambdaTermsTypeInferenceContext {
        globalContext.lambdaDefinitions[definition.name] = definition.expression

        val term = VariableTerm(definition.name)
        val type = solution[term] ?: throw TypeInferenceError(definition.expression, globalContext)
        globalContext.expressionScope[definition.name] = type
        return globalContext
    }

    private fun Lambda.renameArguments(
            renameMap: Map<LambdaName, String>,
            nameMap: MutableMap<String, LambdaName>,
            nameGenerator: NameGenerator
    ): Lambda = when (this) {
        is Lambda.Literal -> renameMap[name]?.let { Lambda.Literal(it) } ?: this
        is Lambda.TypedExpression -> Lambda.TypedExpression(
                expression.renameArguments(renameMap, nameMap, nameGenerator),
                type
        )
        is Lambda.Application -> Lambda.Application(
                function.renameArguments(renameMap, nameMap, nameGenerator),
                arguments.map { it.renameArguments(renameMap, nameMap, nameGenerator) }
        )
        is Lambda.Abstraction -> {
            val newNames = mutableMapOf<LambdaName, String>()
            val newArguments = mutableListOf<String>()
            arguments.forEach {
                val newName = nameGenerator.next(it)
                nameMap[newName] = it
                newNames[it] = newName
                newArguments.add(newName)
            }
            Lambda.Abstraction(
                    newArguments,
                    expression.renameArguments(renameMap + newNames, nameMap, nameGenerator)
            )
        }
    }

    override fun Lambda.getEquations(
            localContext: TypeInferenceLocalContext<LambdaTermsTypeInferenceContext, Lambda>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is Lambda.Literal -> {
            val resultType = localContext.scope[name]
                    ?: throw UnknownExpressionError(name, localContext.globalContext)
            resultType to listOf()
        }
        is Lambda.TypedExpression -> {
            val (resultType, equations) = expression.getEquations(localContext)
            resultType to equations + listOf(AlgebraicEquation(
                    resultType,
                    type.toAlgebraicTerm()
            ))
        }
        is Lambda.Abstraction -> {
            val resultType = VariableTerm(localContext.globalContext.nameGenerator.next("t"))
            val (type, equations) = expression.getEquations(localContext)
            val term = arguments.foldRight(type, { name, res ->
                FunctionTerm(
                        FUNCTION_SIGN,
                        listOf(localContext.scope[name]!!, res)
                )
            })
            resultType to equations + listOf(AlgebraicEquation(
                    resultType,
                    term
            ))
        }
        is Lambda.Application -> {
            val resultType = VariableTerm(localContext.globalContext.nameGenerator.next("t"))
            val (funcType, funcEq) = function.getEquations(localContext)
            val argumentsEq = arguments.map { it.getEquations(localContext) }
            val types = argumentsEq.map { it.first }
            val equations = argumentsEq.flatMap { it.second }
            val term = types.foldRight<AlgebraicTerm, AlgebraicTerm>(resultType, { type, res ->
                FunctionTerm(
                        FUNCTION_SIGN,
                        listOf(type, res)
                )
            })
            resultType to equations + funcEq + listOf(AlgebraicEquation(
                    funcType,
                    term
            ))
        }
    }
}