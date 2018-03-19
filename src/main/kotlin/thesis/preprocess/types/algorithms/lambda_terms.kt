/**
 * Type inference algorithm for lambda terms
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ContextUpdaterByLocal
import thesis.preprocess.LambdaTermsTypeContext
import thesis.preprocess.LocalTypeContext
import thesis.preprocess.ast.Definition
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

class LambdaTermsInferenceAlgorithm(
        override val context: LambdaTermsTypeContext,
        override val dependent: List<ContextUpdaterByLocal<*, Lambda>>
) : TypeInferenceAlgorithm<LambdaTermsTypeContext, Lambda>() {

    override fun Lambda.rename(): Pair<Lambda, Map<String, Lambda>> {
        val nameMap = mutableMapOf<String, Lambda>()
        val renamed = renameArguments(emptyMap(), nameMap, context.nameGenerator)
        return renamed to nameMap
    }

    override fun Definition<Lambda>.getTypeScope() = context.typeDefinitions.keys

    override fun Lambda.getScope() = context.expressionScope + context.typeConstructors +
            getBoundVariables().map { it to VariableTerm(it) }


    override fun updateContext(localContext: LocalTypeContext<Lambda>) {
        val expression = localContext.expression
        val name = localContext.name
        context.lambdaDefinitions[name] = expression

        val term = VariableTerm(name)
        val type = localContext.solution[term] ?: throw TypeInferenceError(expression, context)
        context.expressionScope[name] = type
    }

    override fun getEquations(
            name: String,
            expression: Lambda,
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<Lambda, AlgebraicTerm>
    ): List<AlgebraicEquation> {
        val equationsSystem = super.getEquations(name, expression, scope, expressionTypes)
        // Add type declaration if it exists
        return equationsSystem + (context.expressionScope[name]?.let {
            listOf(AlgebraicEquation(
                    VariableTerm(name),
                    it
            ))
        } ?: emptyList())
    }

    override fun Lambda.getEquations(
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<Lambda, AlgebraicTerm>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> {
        val resultType: AlgebraicTerm
        val equations: List<AlgebraicEquation>
        when (this) {
            is Lambda.Literal -> {
                resultType = if (name.contains(LEARN_PREFIX)) {
                    // TODO: fix this crutch for teachable expression
                    VariableTerm(name)
                } else {
                    scope[name]
                            ?: throw UnknownExpressionError(name, context)
                }
                equations = listOf()
            }
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable expressions should be replaced to literals"
            )
            is Lambda.TypedExpression -> {
                val res = expression.getEquations(scope, expressionTypes)
                resultType = res.first
                equations = res.second + listOf(AlgebraicEquation(
                        resultType,
                        type.toAlgebraicTerm()
                ))
            }
            is Lambda.Abstraction -> {
                resultType = VariableTerm(context.nameGenerator.next("t"))
                val result = expression.getEquations(scope, expressionTypes)
                val term = arguments.foldRight(result.first, { name, res ->
                    FunctionTerm(
                            FUNCTION_SIGN,
                            listOf(scope[name]!!, res)
                    )
                })
                equations = result.second + listOf(AlgebraicEquation(
                        resultType,
                        term
                ))
            }
            is Lambda.Application -> {
                resultType = VariableTerm(context.nameGenerator.next("t"))
                val (funcType, funcEq) = function.getEquations(scope, expressionTypes)
                val argumentsEq = arguments.map { it.getEquations(scope, expressionTypes) }
                val types = argumentsEq.map { it.first }
                val term = types.foldRight<AlgebraicTerm, AlgebraicTerm>(resultType, { type, res ->
                    FunctionTerm(
                            FUNCTION_SIGN,
                            listOf(type, res)
                    )
                })
                equations = argumentsEq.flatMap { it.second } + funcEq + listOf(AlgebraicEquation(
                        funcType,
                        term
                ))
            }
        }
        expressionTypes[this] = resultType
        return resultType to equations
    }

    private fun Lambda.renameArguments(
            renameMap: Map<LambdaName, String>,
            nameMap: MutableMap<String, Lambda>,
            nameGenerator: NameGenerator
    ): Lambda = when (this) {
        is Lambda.Literal -> renameMap[name]?.let { Lambda.Literal(it) } ?: this
        is Lambda.Trainable -> {
            val newName = nameGenerator.next(LEARN_PREFIX)
            nameMap[newName] = this
            Lambda.Literal(newName)
        }
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
                nameMap[newName] = Lambda.Literal(it)
                newNames[it] = newName
                newArguments.add(newName)
            }
            Lambda.Abstraction(
                    newArguments,
                    expression.renameArguments(renameMap + newNames, nameMap, nameGenerator)
            )
        }
    }
    companion object {
        private const val LEARN_PREFIX = "learn"
    }
}