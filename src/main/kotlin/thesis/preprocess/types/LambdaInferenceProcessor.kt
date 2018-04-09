package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.algebraic.term.AlgebraicEquation
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
                .map { (name, type) -> name to type.toRaw().bindVariables() }
                .toMap().toMutableMap()

        data.forEach { (name, expressions) ->

            val rawExpectedType = typeDeclarations[name]?.type?.toRaw()

            val typed = expressions.map { it.inferTypes(scope, algebraicTypes) }
            val types = (rawExpectedType?.let { listOf(it) } ?: emptyList()) + typed.map { it.first }
            val solution = unifyTypes(types, algebraicTypes)
            val finalSubstitution = typed
                    .fold(emptyMap<String, RawType>(), { s, a -> s.compose(a.third)})
                    .compose(solution)
            val rawExpressionType = types.first()
                    .replaceLiterals(finalSubstitution)
            val expressionType = rawExpressionType
                    .toType(typeScope)
                    .bindVariables()
            val cases = typed.map { (_, case) ->
                val boundPatterns = case.patterns.map {
                    it.replaceLiterals(finalSubstitution)
                            .bindLiterals(algebraicTypes)
                            .modifyType { it.toType(typeScope) }
                }
                val boundLambda = case.lambda.replaceLiterals(finalSubstitution)
                        .bindLiterals(algebraicTypes)
                        .modifyType { it.toType(typeScope) }
                LambdaWithPatterns(boundPatterns, boundLambda)
            }

            scope[name] = rawExpressionType.bindLiterals(algebraicTypes).bindVariables()
            result[name] = InferredLambda(
                    expressionType,
                    cases
            )
        }
        return result
    }

    private fun LambdaWithPatterns<UntypedLambda, UntypedPattern>.inferTypes(
            scope: Map<String, Parametrised<RawType>>,
            algebraicTypes: Set<String>
    ): Triple<RawType, LambdaWithPatterns<TypedLambda<RawType>, TypedPattern<RawType>>, Map<String, RawType>> {
        val patternsVariables = patterns.flatMap { it.getVariables() }
        val typedVariables = patternsVariables.map {
            it to RawType.Literal(nameGenerator.next(it)).bindVariables()
        }
        val variables = typedVariables.toMap().toMutableMap()
        val definedTypes = algebraicTypes + patternsVariables.toSet()

        // Infer types for patterns and lambda
        val preInferredPatterns = patterns.map { it.inferTypes(scope, variables, algebraicTypes) }
        val preInferredLambda = lambda.inferType(scope, variables, algebraicTypes, definedTypes)

        val inferredPatterns = preInferredPatterns.map { it.first }
        val inferredLambda = preInferredLambda.first

        val subst = preInferredPatterns
                .fold(emptyMap<String, RawType>(), { s, a -> s.compose(a.second)})
                .compose(preInferredLambda.second)

        val expressionType = preInferredPatterns.foldRight(
                preInferredLambda.first.type.type,
                { arg, res -> RawType.Function(arg.first.type.type, res) }
        )
        return Triple(expressionType, LambdaWithPatterns(inferredPatterns, inferredLambda), subst)
    }

    private fun UntypedPattern.inferTypes(
            scope: Map<String, Parametrised<RawType>>,
            variables: Map<LambdaName, Parametrised<RawType>>,
            algebraicTypes: Set<String>
    ): Pair<TypedPattern<RawType>, Map<String, RawType>> = when (this) {
        is UntypedPattern.Variable -> {
            val variableType = variables[name] ?: throw UnknownExpressionError(name)
            TypedPattern.Variable(this, variableType) to emptyMap()
        }
        is UntypedPattern.Object -> {
            val returnType = scope[name] ?: throw UnknownExpressionError(name)
            // Object can't contain type variables
            TypedPattern.Object(this, returnType) to emptyMap()
        }
        is UntypedPattern.Constructor -> {
            val expectedType = scope[name] ?: throw UnknownExpressionError(name)
            // Types can't contain parameters
            val instantiated = expectedType.type

            val newType = RawType.Literal(nameGenerator.next(TYPE_PREFIX))
            val inferredArgs = arguments.map { it.inferTypes(scope, variables, algebraicTypes) }

            val subst = inferredArgs.fold(emptyMap<String, RawType>(), { s, arg -> s.compose(arg.second)})
            val args = inferredArgs.map { it.first }
            val gotType = args.foldRight<TypedPattern<RawType>, RawType>(
                    newType,
                    { arg, res -> RawType.Function(arg.type.type, res) }
            )

            val unified = unifyTypes(instantiated, gotType, algebraicTypes)
            TypedPattern.Constructor(
                    name,
                    args,
                    newType.bindVariables()
            ) to subst.compose(unified)
        }
    }

    private fun TypedPattern<RawType>.bindLiterals(definedTypes: Set<String>) =
            modifyType { it.bindLiterals(definedTypes) }

    private fun UntypedLambda.inferType(
            scope: Map<String, Parametrised<RawType>>,
            variables: Map<LambdaName, Parametrised<RawType>>,
            algebraicTypes: Set<String>,
            definedTypes: Set<String>
    ): Pair<TypedLambda<RawType>, Map<String, RawType>> = when (this) {
        is UntypedLambda.Literal -> {
            val variable = variables[name]?.let {
                TypedLambda.Literal(this, emptyMap(), it)
            }
            val type = scope[name]?.let {
                val (instantiated, map) = it.instantiateVariables(nameGenerator)
                TypedLambda.Literal(this, map, instantiated)
            }
            val expression = variable ?: type ?: throw UnknownExpressionError(name)
            expression to emptyMap()
        }
        is UntypedLambda.Trainable -> {
            val type = RawType.Literal(nameGenerator.next(TRAIN_PREFIX))
            val expression = TypedLambda.Trainable(this, type.bindVariables())
            expression to emptyMap()
        }
        is UntypedLambda.TypedExpression -> {
            val (inferred, subst) = expression.inferType(scope, variables, algebraicTypes, definedTypes)
            val (expected, _) = type.instantiateVariables(nameGenerator)
            val got = inferred.type
            val solution = unifyTypes(
                    expected.type,
                    got.type,
                    algebraicTypes
            )
            inferred.replaceLiterals(solution) to subst.compose(solution)
        }
        is UntypedLambda.Abstraction -> {
            val newVariables = (variables + arguments.map {
                it.name to RawType.Literal(nameGenerator.next(it.name)).bindVariables()
            }).toMutableMap()
            val newArguments = arguments.map {
                it.inferType(scope, newVariables, algebraicTypes, definedTypes).first as TypedLambda.Literal
            }
            // We expect types of arguments to be literals
            val newTypes = newArguments.map { (it.type.type as RawType.Literal).name }.toSet()
            val newDefined = definedTypes + newTypes
            val (result, subst) = expression.inferType(scope, newVariables, algebraicTypes, newDefined)
            val term = newArguments
                    .foldRight(result.type.type, { arg, res ->
                        RawType.Function(arg.type.type, res)
                    })
            TypedLambda.Abstraction(newArguments, result, term.bindVariables()) to subst
        }
        is UntypedLambda.LetAbstraction -> {
            val newScope = HashMap(scope)
            val newBindings = mutableListOf<TypedLambda.LetAbstraction.Binding<RawType>>()
            var subst = emptyMap<String, RawType>()
            bindings.forEach {
                val (inferred, bindSubst) = it.expression.inferType(newScope, variables, algebraicTypes, definedTypes)
                val bound = inferred
                        .replaceLiterals(bindSubst)
                        .bindLiterals(definedTypes)
                val newBinding = TypedLambda.LetAbstraction.Binding(
                        it.name,
                        bound
                )
                newScope[it.name] = bound.type
                newBindings.add(newBinding)
                subst = subst.compose(bindSubst)
            }
            val (inferred, exprSubst) = expression.inferType(newScope, variables, algebraicTypes, definedTypes)
            TypedLambda.LetAbstraction(
                    newBindings,
                    inferred,
                    inferred.type
            ) to subst.compose(exprSubst)
        }
        is UntypedLambda.Application -> {
            val (function, funcSubst) = function.inferType(scope, variables, algebraicTypes, definedTypes)
            val inferredArguments = arguments.map { it.inferType(scope, variables, algebraicTypes, definedTypes) }
            val resultType = RawType.Literal(nameGenerator.next(TYPE_PREFIX))

            val subst = inferredArguments.fold(funcSubst, { s, arg -> s.compose(arg.second)})
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
            TypedLambda.Application(
                    function,
                    arguments,
                    resultType.bindVariables()
            ).replaceLiterals(solution) to subst.compose(solution)
        }
    }

    private fun unifyTypes(
            first: RawType,
            second: RawType,
            definedTypes: Set<String>
    ): Map<TypeName, RawType> = unifyTypes(listOf(first, second), definedTypes)

    private fun unifyTypes(types: List<RawType>, definedTypes: Set<String>): Map<TypeName, RawType> {
        if (types.size < 2) {
            return emptyMap()
        }
        val terms = types.map { it.toAlgebraicTerm() }
        val first = terms.first()
        val rest = terms.drop(1)
        return rest.map { AlgebraicEquation(first, it) }
                .inferTypes(definedTypes)
                .mapValues { (_, v) -> RawType.fromAlgebraicTerm(v) }
    }

    private fun Map<String, RawType>.compose(
            other: Map<String, RawType>
    ): Map<String, RawType> = other + mapValues { (_, value) -> value.replaceLiterals(other)}


    private fun TypedLambda<RawType>.bindLiterals(definedTypes: Set<String>) = modifyType { it.bindLiterals(definedTypes) }

    companion object {
        private const val TRAIN_PREFIX = "learn"
        private const val TYPE_PREFIX = "t"
    }
}