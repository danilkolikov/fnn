/**
 * Type inference algorithm for lambda terms
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.Definition
import thesis.preprocess.ast.LambdaDefinition
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

interface LambdaTermsTypeContext : AlgebraicTypeContext {

    val lambdaDefinitions: MutableMap<LambdaName, Lambda>

    val expressionScope: MutableMap<LambdaName, AlgebraicTerm>
}

data class LambdaTermsLocalContext(
        override val expression: Definition<Lambda>,
        val nameMap: Map<String, Lambda>,
        override val typeScope: Set<String>,
        override val scope: Map<String, AlgebraicTerm>,
        override var solution: Map<VariableTerm, AlgebraicTerm> = mapOf()
) : LocalTypeContext<Definition<Lambda>>

class LambdaTermsInferenceAlgorithm(
        override val typeContext: LambdaTermsTypeContext
) : TypeInferenceAlgorithm<LambdaTermsTypeContext, Lambda, LambdaTermsLocalContext>() {

    override fun getLocalContext(expression: Definition<Lambda>): LambdaTermsLocalContext {
        val definedTypes = typeContext.typeDefinitions.keys
        val nameMap = mutableMapOf<String, Lambda>()
        val renamed = expression.expression.renameArguments(emptyMap(), nameMap, typeContext.nameGenerator)
        val scope = typeContext.expressionScope + typeContext.typeConstructors +
                nameMap.keys.map { it to VariableTerm(it) }
        return LambdaTermsLocalContext(
                LambdaDefinition(expression.name, renamed),
                nameMap,
                definedTypes,
                scope
        )
    }

    override fun getEquationsSystem(localContext: LambdaTermsLocalContext): List<AlgebraicEquation> {
        val equationsSystem = super.getEquationsSystem(localContext)
        // Add type declaration if it exists
        return equationsSystem + (typeContext.expressionScope[localContext.expression.name]?.let {
            listOf(AlgebraicEquation(
                    VariableTerm(localContext.expression.name),
                    it
            ))
        } ?: emptyList())
    }

    override fun updateContext(localContext: LambdaTermsLocalContext) {
        val definition = localContext.expression
        typeContext.lambdaDefinitions[definition.name] = definition.expression

        val term = VariableTerm(definition.name)
        val type = localContext.solution[term] ?: throw TypeInferenceError(definition.expression, typeContext)
        typeContext.expressionScope[definition.name] = type
    }

    private fun Lambda.renameArguments(
            renameMap: Map<LambdaName, String>,
            nameMap: MutableMap<String, Lambda>,
            nameGenerator: NameGenerator
    ): Lambda = when (this) {
        is Lambda.Literal -> renameMap[name]?.let { Lambda.Literal(it) } ?: this
        is Lambda.Trainable -> {
            val newName = nameGenerator.next("learn")
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

    override fun Lambda.getEquations(
            localContext: LambdaTermsLocalContext
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is Lambda.Literal -> {
            val resultType = localContext.scope[name]
                    ?: throw UnknownExpressionError(name, typeContext)
            resultType to listOf()
        }
        is Lambda.Trainable -> throw IllegalStateException(
                "Trainable expressions should be replaced to literals"
        )
        is Lambda.TypedExpression -> {
            val (resultType, equations) = expression.getEquations(localContext)
            resultType to equations + listOf(AlgebraicEquation(
                    resultType,
                    type.toAlgebraicTerm()
            ))
        }
        is Lambda.Abstraction -> {
            val resultType = VariableTerm(typeContext.nameGenerator.next("t"))
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
            val resultType = VariableTerm(typeContext.nameGenerator.next("t"))
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