package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Type
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.results.InferredLambda
import thesis.preprocess.results.InferredLambdaImpl
import thesis.preprocess.results.InferredType
import thesis.preprocess.results.RenamedLambda
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm
import java.util.*

/**
 * Type inference for lambda expressions
 *
 * @author Danil Kolikov
 */
class LambdaInferenceProcessor(
        private val nameGenerator: NameGenerator,
        private val typeScope: Map<TypeName, InferredType>,
        private val typeDeclarations: Map<LambdaName, Type>
) : Processor<Map<LambdaName, RenamedLambda>, Map<LambdaName, InferredLambda>> {
    override fun process(data: Map<LambdaName, RenamedLambda>): Map<LambdaName, InferredLambda> {
        val result = mutableMapOf<LambdaName, InferredLambda>()
        val definedTypes = typeScope.keys
        val constructors = typeScope.values
                .flatMap { it.constructors.entries.map { it.toPair() } }
                .map { (name, type) -> name to type.toAlgebraicTerm() }
                .toMap()
        val lambdaType = mutableMapOf<LambdaName, Type>()
        data.forEach { name, value ->

            val scope = (constructors + lambdaType.mapValues { (_, value) -> value.toAlgebraicTerm() })
                    .toMutableMap()

            // If type on function is already declared, add it to scope
            typeDeclarations[name]?.let { type -> scope[name] = type.toAlgebraicTerm() }

            val function = VariableTerm(name)
            val expressionTypes = IdentityHashMap<Lambda, Type>()
            value.expressions.forEach { expression ->
                val localScope = scope + expression.getBoundVariables()
                        .map { it to VariableTerm(it) }
                        .toMap()
                val localExpressionsTypes = IdentityHashMap<Lambda, AlgebraicTerm>()
                val (resultType, equations) = expression.getEquations(localScope, localExpressionsTypes, value.nameMap)
                val system = equations + listOf(AlgebraicEquation(function, resultType))
                val solution = system.inferTypes(definedTypes)
                val type = solution[name] ?: throw TypeInferenceError(expression)

                // Type of the first expression with such name is considered correct
                scope.putIfAbsent(name, type)

                // Save types of sub-expressions
                localExpressionsTypes.forEach { (lambda, type) ->
                    expressionTypes[lambda] = type.replace(solution).toType()
                }
            }

            val type = scope[name]?.toType() ?: throw TypeInferenceErrorWithoutContext()
            lambdaType[name] = type

            val inferred = InferredLambdaImpl(value, type, expressionTypes)
            result[name] = inferred
        }
        return result
    }

    private fun Lambda.getEquations(
            scope: Map<String, AlgebraicTerm>,
            expressionTypes: MutableMap<Lambda, AlgebraicTerm>,
            nameMap: Map<String, Lambda>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> {
        val resultType: AlgebraicTerm
        val equations: List<AlgebraicEquation>
        when (this) {
            is Lambda.Literal -> {
                resultType = if (nameMap[name] === Lambda.Trainable) {
                    // TODO: fix this crutch for trainable expression
                    VariableTerm(name)
                } else {
                    scope[name]
                            ?: throw UnknownExpressionError(name)
                }
                equations = listOf()
            }
            is Lambda.Trainable -> throw IllegalStateException(
                    "Trainable expressions should be replaced to literals"
            )
            is Lambda.TypedExpression -> {
                val res = expression.getEquations(scope, expressionTypes, nameMap)
                resultType = res.first
                equations = res.second + listOf(AlgebraicEquation(
                        resultType,
                        type.toAlgebraicTerm()
                ))
            }
            is Lambda.Abstraction -> {
                resultType = VariableTerm(nameGenerator.next("t"))
                val result = expression.getEquations(scope, expressionTypes, nameMap)
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
                resultType = VariableTerm(nameGenerator.next("t"))
                val (funcType, funcEq) = function.getEquations(scope, expressionTypes, nameMap)
                val argumentsEq = arguments.map { it.getEquations(scope, expressionTypes, nameMap) }
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
}