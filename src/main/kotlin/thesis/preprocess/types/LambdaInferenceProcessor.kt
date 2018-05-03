package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.lambda.LambdaWithPatterns
import thesis.preprocess.expressions.lambda.typed.TypedLambda
import thesis.preprocess.expressions.lambda.typed.TypedPattern
import thesis.preprocess.expressions.lambda.untyped.UntypedLambda
import thesis.preprocess.expressions.lambda.untyped.UntypedPattern
import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.expressions.type.raw.instantiateVariables
import thesis.preprocess.results.InferredLambda
import thesis.preprocess.results.UntypedLambdaWithPatterns
import thesis.utils.NameGenerator

/**
 * Type inference for lambda expressions
 *
 * @author Danil Kolikov
 */
class LambdaInferenceProcessor(
        private val nameGenerator: NameGenerator,
        private val typeScope: Map<TypeName, AlgebraicType>,
        private val typeDeclarations: Map<LambdaName, Parametrised<Type>>
) : Processor<LinkedHashMap<LambdaName, List<UntypedLambdaWithPatterns>>,
        LinkedHashMap<LambdaName, InferredLambda>> {

    override fun process(
            data: LinkedHashMap<LambdaName, List<UntypedLambdaWithPatterns>>
    ): LinkedHashMap<LambdaName, InferredLambda> {
        val result = LinkedHashMap<LambdaName, InferredLambda>()

        val algebraicTypes = typeScope.keys
        val scope = typeScope
                .flatMap { (_, type) -> type.constructors.entries }
                .map { (name, constructor) -> name to constructor.type.modifyType { it.toRaw() } }
                .toMap().toMutableMap()

        data.forEach { (name, expressions) ->

            val rawExpectedType = typeDeclarations[name]?.type?.toRaw(withVariables = false)

            // Assume every function to be recursive
            val recType = RawType.Literal(nameGenerator.next(name))
            val variables = mapOf(name to Parametrised(emptyList(), recType, emptyMap()))
            val varTypes = setOf(recType.name)

            val typed = expressions.map { it.inferTypes(scope, variables, varTypes, algebraicTypes) }

            // If there was recursive call in any of cases, the definition should be recursive
            val varScope = mergeSubstitutions(typed.map { it.third }, algebraicTypes)
            val isRecursive = !varScope.isEmpty()
            val recTypeRenamed = recType.replaceLiterals(varScope)

            // Unify types of all cases
            // If there was a type declaration, add it to the list for unification
            // If there was a recursive call, add it's type to the list
            val types = typed.map { it.first } +
                    (rawExpectedType?.let { listOf(it) } ?: emptyList()) +
                    (if (isRecursive) listOf(recTypeRenamed) else emptyList())

            val solution = types.unifyTypes(algebraicTypes)

            val rawExpressionType = types.first().replaceLiterals(solution)
            val expressionType = rawExpressionType.toType(typeScope)
            val typeVariables = expressionType.getVariables().toList()

            val cases = typed.map { (_, case) ->
                val boundPatterns = case.patterns.map {
                    it.replaceLiterals(solution)
                            .modifyType { it.bindLiterals(algebraicTypes).toType(typeScope) }
                }
                val boundLambda = case.lambda.replaceLiterals(solution)
                        .modifyType { it.bindLiterals(algebraicTypes).toType(typeScope) }
                LambdaWithPatterns(boundPatterns, boundLambda)
            }

            scope[name] = Parametrised(
                    typeVariables,
                    rawExpressionType.bindLiterals(algebraicTypes),
                    typeVariables.map { it to RawType.Variable(it) }.toMap()
            )
            result[name] = InferredLambda(
                    Parametrised(
                            typeVariables,
                            expressionType,
                            typeVariables.map { it to Type.Variable(it) }.toMap()
                    ),
                    cases,
                    isRecursive
            )
        }
        return result
    }

    private fun LambdaWithPatterns<UntypedLambda, UntypedPattern>.inferTypes(
            scope: Map<String, Parametrised<RawType>>,
            variables: Map<LambdaName, Parametrised<RawType>>,
            variableTypes: Set<String>,
            algebraicTypes: Set<String>
    ): Triple<
            RawType,
            LambdaWithPatterns<TypedLambda<RawType>, TypedPattern<RawType>>,
            Map<LambdaName, RawType>> {
        val patternsVariables = patterns.flatMap { it.getVariables() }
        val varTypes = patternsVariables.map { it to nameGenerator.next(it) }
        val typedVariables = varTypes.map { (name, type) ->
            val parametrised = Parametrised(
                    emptyList(),
                    RawType.Literal(type),
                    emptyMap()
            )
            name to parametrised
        }
        val newVariableTypes = varTypes.map { it.second }.toSet()
        val newVariables = typedVariables.toMap().toMutableMap()
        val definedTypes = algebraicTypes + patternsVariables.toSet()

        // Infer types for patterns and lambda
        val preInferredPatterns = patterns.map {
            it.inferTypes(scope, newVariables, newVariableTypes, definedTypes)
        }
        val (preInferredLambda, varScope) = lambda.inferType(
                scope,
                variables + newVariables,
                variableTypes + newVariableTypes,
                algebraicTypes,
                definedTypes
        )

        // Replace variables in patterns according to varScope
        val merged = mergeSubstitutions(listOf(varScope) + preInferredPatterns.map { it.second }, algebraicTypes)

        val inferredPatterns = preInferredPatterns.map { it.first.replaceLiterals(merged) }
        val inferredLambda = preInferredLambda.replaceLiterals(merged)
        val expressionType = inferredPatterns.foldRight(
                inferredLambda.type.type,
                { arg, res -> RawType.Function(arg.type.type, res) }
        )
        val newScope = varScope.filterKeys { variableTypes.contains(it) }
        return Triple(
                expressionType,
                LambdaWithPatterns(inferredPatterns, inferredLambda),
                newScope
        )
    }

    private fun UntypedPattern.inferTypes(
            scope: Map<String, Parametrised<RawType>>,
            variables: Map<LambdaName, Parametrised<RawType>>,
            variableTypes: Set<String>,
            algebraicTypes: Set<String>
    ): Pair<TypedPattern<RawType>, Map<String, RawType>> = when (this) {
        is UntypedPattern.Variable -> {
            val variableType = variables[name] ?: throw UnknownExpressionError(name)
            TypedPattern.Variable(this, variableType) to emptyMap()
        }
        is UntypedPattern.Object -> {
            val returnType = scope[name] ?: throw UnknownExpressionError(name)
            val instantiated = returnType.instantiateVariables(nameGenerator)
            TypedPattern.Object(this, instantiated) to emptyMap()
        }
        is UntypedPattern.Constructor -> {
            val expectedType = scope[name] ?: throw UnknownExpressionError(name)
            val instantiated = expectedType.instantiateVariables(nameGenerator)

            val newType = RawType.Literal(nameGenerator.next(TYPE_PREFIX))
            val inferredArgs = arguments.map { it.inferTypes(scope, variables, variableTypes, algebraicTypes) }

            val args = inferredArgs.map { it.first }
            val gotType = args.foldRight<TypedPattern<RawType>, RawType>(
                    newType,
                    { arg, res -> RawType.Function(arg.type.type, res) }
            )

            val solution = unifyTypes(instantiated.type, gotType, algebraicTypes)
            val parametrised = Parametrised(
                    emptyList(),
                    newType,
                    emptyMap()
            )
            val varScope = inferredArgs.flatMap {
                it.second.mapValues { (_, v) -> v.replaceLiterals(solution) }.entries.map { it.toPair() }
            }.toMap() + solution
            val newVarScope = varScope.filterKeys { variableTypes.contains(it) }
            TypedPattern.Constructor(
                    name,
                    args,
                    parametrised
            ).replaceLiterals(solution) to newVarScope
        }
    }

    private fun UntypedLambda.inferType(
            scope: Map<String, Parametrised<RawType>>,
            variables: Map<LambdaName, Parametrised<RawType>>,
            variableTypes: Set<String>,
            algebraicTypes: Set<String>,
            definedTypes: Set<String>
    ): Pair<TypedLambda<RawType>, Map<String, RawType>> = when (this) {
        is UntypedLambda.Literal -> {
            val variable = variables[name]?.let {
                TypedLambda.Literal(this, it)
            }
            val type = scope[name]?.let {
                val instantiated = it.instantiateVariables(nameGenerator)
                TypedLambda.Literal(this, instantiated)
            }
            val expression = variable ?: type ?: throw UnknownExpressionError(name)
            expression to emptyMap()
        }
        is UntypedLambda.Trainable -> {
            // Trainable should always be a function
            val fromType = RawType.Literal(nameGenerator.next(TRAIN_PREFIX))
            val toType = RawType.Literal(nameGenerator.next(TRAIN_PREFIX))
            val type = RawType.Function(fromType, toType)
            val parametrised = Parametrised(
                    emptyList(),
                    type,
                    emptyMap()
            )
            TypedLambda.Trainable(this, parametrised) to emptyMap()
        }
        is UntypedLambda.TypedExpression -> {
            type.type.checkWellKinded(typeScope)
            val (inferred, varScope) = expression.inferType(
                    scope, variables, variableTypes, algebraicTypes, definedTypes
            )
            val expected = type.instantiateVariables(nameGenerator)
            val got = inferred.type
            val solution = unifyTypes(
                    expected.type,
                    got.type,
                    algebraicTypes
            )
            val merged = mergeSubstitutions(listOf(varScope, solution), algebraicTypes)
            val newVarScope = merged.filterKeys { variableTypes.contains(it) }
            inferred.replaceLiterals(solution) to newVarScope
        }
        is UntypedLambda.Abstraction -> {
            val newVariableNames = arguments.map {
                it.name to nameGenerator.next(it.name)
            }
            val newVariables = variables + newVariableNames.map { (name, type) ->
                val parametrised = Parametrised(
                        emptyList(),
                        RawType.Literal(type),
                        emptyMap()
                )
                name to parametrised
            }
            val newArguments = arguments.map {
                it.inferType(
                        scope, newVariables, variableTypes, algebraicTypes, definedTypes
                ).first
            }
            val newVariableTypes = variableTypes + newVariableNames.map { it.second }.toSet()
            // We expect types of arguments to be literals
            val newTypes = newArguments.map { (it.type.type as RawType.Literal).name }.toSet()
            val newDefined = definedTypes + newTypes
            val (result, varScope) = expression.inferType(
                    scope, newVariables, newVariableTypes, algebraicTypes, newDefined
            )
            // Replace types of variables according to varScope
            val replacedArguments = newArguments.map { it.replaceLiterals(varScope) as TypedLambda.Literal }
            val term = replacedArguments
                    .foldRight(result.type.type, { arg, res ->
                        RawType.Function(arg.type.type, res)
                    })
            // Result contains all required information
            val parametrised = Parametrised(
                    emptyList(),
                    term,
                    emptyMap()
            )
            // New variable scope should contain information relevant to variables that were bound before
            val newVarScope = varScope.filterKeys { variableTypes.contains(it) }
            TypedLambda.Abstraction(
                    replacedArguments,
                    result,
                    parametrised
            ) to newVarScope
        }
        is UntypedLambda.LetAbstraction -> {
            val newScope = HashMap(scope)
            val newBindings = mutableListOf<TypedLambda.LetAbstraction.Binding<RawType>>()
            val varScopes = mutableListOf<Map<String, RawType>>()
            bindings.forEach {
                val (inferred, varScope) = it.expression.inferType(
                        newScope, variables, variableTypes, algebraicTypes, definedTypes
                )
                val bound = inferred.bindLiterals(definedTypes)
                val newBinding = TypedLambda.LetAbstraction.Binding(
                        it.name,
                        bound
                )
                newScope[it.name] = bound.type
                newBindings.add(newBinding)
                varScopes.add(varScope)
            }
            val (inferred, expVarScope) = expression.inferType(
                    newScope, variables, variableTypes, algebraicTypes, definedTypes
            )

            // Check for conflicts in varScopes
            val merged = mergeSubstitutions(varScopes + listOf(expVarScope), algebraicTypes)
            val newVarScope = merged.filterKeys { variableTypes.contains(it) }
            TypedLambda.LetAbstraction(
                    newBindings,
                    inferred,
                    inferred.type
            ) to newVarScope
        }
        is UntypedLambda.RecAbstraction -> {
            val argumentType = RawType.Literal(nameGenerator.next(argument.name))
            val parametrised = Parametrised(
                    emptyList(),
                    argumentType,
                    emptyMap()
            )
            val newVariables = variables + mapOf(argument.name to parametrised)
            val newVarTypes = variableTypes + setOf(argumentType.name)
            val inferredArgument = argument.inferType(
                    scope, newVariables, newVarTypes, algebraicTypes, definedTypes
            ).first
            val (inferredExpr, varScope) = expression.inferType(
                    scope, newVariables, newVarTypes, algebraicTypes, definedTypes
            )
            val newParametrised = inferredArgument.replaceLiterals(varScope)
            // Check that type of argument is equal to the type of expression
            val solution = unifyTypes(
                    newParametrised.type.type,
                    inferredExpr.type.type,
                    algebraicTypes
            )
            val replacedArgument = newParametrised.replaceLiterals(solution) as TypedLambda.Literal
            val newVarScope = varScope.filterKeys { variables.containsKey(it) }
            TypedLambda.RecAbstraction(
                    replacedArgument,
                    inferredExpr,
                    inferredExpr.type
            ) to newVarScope
        }
        is UntypedLambda.Application -> {
            val (function, funcVarScope) = function.inferType(
                    scope, variables, variableTypes, algebraicTypes, definedTypes
            )
            val inferredArguments = arguments.map {
                it.inferType(scope, variables, variableTypes, algebraicTypes, definedTypes)
            }
            val resultType = RawType.Literal(nameGenerator.next(TYPE_PREFIX))

            val arguments = inferredArguments.map { it.first }
            val expected = arguments.foldRight<TypedLambda<RawType>, RawType>(
                    resultType,
                    { arg, res ->
                        RawType.Function(
                                arg.type.type, res
                        )
                    }
            )
            val solution = unifyTypes(expected, function.type.type, algebraicTypes)

            // Any operand may bind variables from the scope in a different way
            // So we should unify them to avoid collisions
            val substitutions = listOf(solution, funcVarScope) + inferredArguments.map { it.second }
            val merged = mergeSubstitutions(substitutions, algebraicTypes)
            val newVarScope = merged.filterKeys { variableTypes.contains(it) }
                    .mapValues { (_, v) -> v.replaceLiterals(solution) }
            val parametrised = Parametrised(
                    emptyList(),
                    resultType,
                    emptyMap()
            )
            TypedLambda.Application(
                    function,
                    arguments,
                    parametrised
            ).replaceLiterals(solution) to newVarScope
        }
    }

    private fun TypedLambda<RawType>.bindLiterals(definedTypes: Set<String>) = modifyType(false) {
        it.bindLiterals(definedTypes)
    }

    companion object {
        private const val TRAIN_PREFIX = "learn"
        private const val TYPE_PREFIX = "t"
    }
}