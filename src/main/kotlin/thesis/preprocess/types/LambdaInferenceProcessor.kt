package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Pattern
import thesis.preprocess.expressions.Type
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.results.*
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
        private val typeScope: List<InferredType>,
        private val typeDeclarations: List<RenamedTypeDeclaration>
) : Processor<List<RenamedLambda>, List<InferredLambda>> {
    override fun process(data: List<RenamedLambda>): List<InferredLambda> {
        val result = mutableListOf<InferredLambda>()

        val definedTypes = typeScope.map { it.name }.toSet()
        val constructors = typeScope
                .flatMap { it.constructors.entries.map { it.toPair() } }
                .map { (name, type) -> name to type.toAlgebraicTerm() }
                .toMap()
        val lambdaType = mutableMapOf<LambdaName, Type>()
        data.forEach { value ->
            val name = value.name
            val scope = (constructors + lambdaType.mapValues { (_, value) -> value.toAlgebraicTerm() })
                    .toMutableMap()

            // If type on function is already declared, add it to scope
            typeDeclarations.find { it.name == name }?.let { scope[name] = it.type.toAlgebraicTerm() }

            val function = VariableTerm(name)
            val expressionTypes = IdentityHashMap<Lambda, Type>()
            value.expressions.forEach { (patterns, lambda) ->

                val patternsVariables = patterns.flatMap { it.getVariables() }
                val localScope = scope + (patternsVariables + lambda.getBoundVariables())
                        .map { it to VariableTerm(it) }
                        .toMap()

                // Infer types for patterns
                val system = mutableListOf<AlgebraicEquation>()
                val patternsTypes = mutableListOf<AlgebraicTerm>()

                patterns.forEach { pattern ->
                    val (resultType, equations) = pattern.getEquations(localScope, nameGenerator)
                    system.addAll(equations)
                    patternsTypes.add(resultType)
                }

                val localExpressionsTypes = IdentityHashMap<Lambda, AlgebraicTerm>()
                val (resultType, equations) = lambda.getEquations(localScope, localExpressionsTypes, value.nameMap)

                // Function type (that uses pattern arguments)
                val functionType = patternsTypes.foldRight(resultType, { arg, res ->
                    FunctionTerm(
                            FUNCTION_SIGN,
                            listOf(arg, res)
                    )
                })
                system.addAll(equations)
                system.add(AlgebraicEquation(
                        function,
                        functionType
                ))
                // Add previously declared type of expression
                scope[name]?.let {
                    system.add(AlgebraicEquation(
                            function,
                            it
                    ))
                }

                val solution = system.inferTypes(definedTypes)
                val type = solution[name] ?: throw TypeInferenceError(lambda)

                // Type of the first expression with such name is considered correct
                scope.putIfAbsent(name, type)

                // Save types of sub-expressions
                localExpressionsTypes.forEach { (localLambda, type) ->
                    val replacedType = type.replace(solution).toType()
                    if (localLambda is Lambda.Trainable) {
                        // Type of trainable should be a function
                        // whose arguments and return type are algebraic types
                        val allAlgebraic = replacedType is Type.Function && replacedType.getOperands().all {
                            it is Type.Literal && definedTypes.contains(it.name)
                        }
                        if (!allAlgebraic) {
                            throw UnsupportedTrainableType(lambda, replacedType)
                        }
                    }
                    expressionTypes[localLambda] = replacedType
                }
            }

            val type = scope[name]?.toType() ?: throw TypeInferenceErrorWithoutContext()
            lambdaType[name] = type

            val inferred = InferredLambdaImpl(value, type, expressionTypes)
            result.add(inferred)
        }
        return result
    }

    private fun Pattern.getEquations(
            scope: Map<String, AlgebraicTerm>,
            nameGenerator: NameGenerator
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is Pattern.Variable -> {
            val returnType = scope[name] ?: throw UnknownExpressionError(name)
            returnType to emptyList()
        }
        is Pattern.Object -> {
            val returnType = scope[name] ?: throw UnknownExpressionError(name)
            returnType to emptyList()
        }
        is Pattern.Constructor -> {
            val returnType = VariableTerm(nameGenerator.next(name))
            val expectedType = scope[name] ?: throw UnknownExpressionError(name)
            val arguments = arguments.map { it.getEquations(scope, nameGenerator) }
            val argumentTypes = arguments.map { it.first }
            val equations = arguments.flatMap { it.second }
            val gotType = argumentTypes.foldRight<AlgebraicTerm, AlgebraicTerm>(
                    returnType,
                    { arg, res -> FunctionTerm(FUNCTION_SIGN, listOf(arg, res)) }
            )

            returnType to (equations + listOf(AlgebraicEquation(
                    expectedType,
                    gotType
            )))
        }
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
                resultType = scope[name] ?: throw UnknownExpressionError(name)
                equations = listOf()
            }
            is Lambda.Trainable -> {
                resultType = VariableTerm(nameGenerator.next(TRAIN_PREFIX))
                equations = listOf()
            }
            is Lambda.TypedExpression -> {
                val res = expression.getEquations(scope, expressionTypes, nameMap)
                resultType = res.first
                equations = res.second + listOf(AlgebraicEquation(
                        resultType,
                        type.toAlgebraicTerm()
                ))
            }
            is Lambda.Abstraction -> {
                resultType = VariableTerm(nameGenerator.next(TYPE_PREFIX))
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
                resultType = VariableTerm(nameGenerator.next(TYPE_PREFIX))
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

    companion object {
        private const val TRAIN_PREFIX = "learn"
        private const val TYPE_PREFIX = "t"
    }
}