package thesis.preprocess.types

import thesis.preprocess.ast.*

/**
 * Algorithm for type inference
 */

sealed class SimpleType

data class TypeTerminal(val name: String) : SimpleType() {
    override fun toString() = name
}

data class TypeFunction(val from: SimpleType, val to: SimpleType) : SimpleType() {
    override fun toString() = "($from $FUNCTION_SIGN $to)"
}

fun LambdaProgram.inferSimpleType(): Map<LambdaName, SimpleType> {
    val nameGenerator = NameGenerator("a")
    var globalScope = listOf<AlgebraicEquation>()
    val definedNames = mutableSetOf<String>()
    val definedTypes = mutableSetOf<TypeName>()
    for (expression in expressions) {
        definedNames.add(expression.name)
        if (expression is TypeDefinition) {
            definedTypes += expression.name
            definedNames += expression.getNames()
        }
        val system = when (expression) {
            is TypeDefinition -> getTypeContextSystem(expression, nameGenerator)
            is LambdaDefinition -> getLambdaContextSystem(expression, nameGenerator)
        }
        val solution = solveSystem(globalScope + system)
        val definedTypeNames = definedTypes
                .map { VariableTerm(it) }
                .filter { solution.containsKey(it) }
                .map { solution[it] as VariableTerm to it }
                .toMap()
        val readableSolution = solution
                .filterKeys { definedNames.contains(it.name) }
                .filterKeys { !definedTypes.contains(it.name) }
                .mapValues { (_, value) -> value.replace(definedTypeNames) }
                .map { (key, value) -> AlgebraicEquation(key, value) }
        globalScope = readableSolution
    }

    return globalScope
            .map { (type, value) -> (type as VariableTerm).name to value.toSimpleType() }
            .toMap()
}

private const val FUNCTION_SIGN = "→"

private fun getTypeContextSystem(
        typeDefinition: TypeDefinition,
        nameGenerator: NameGenerator
): List<AlgebraicEquation> {
    val (name, expr) = typeDefinition
    val (type, system) = expr.getEquations(nameGenerator)
    return system + AlgebraicEquation(
            VariableTerm(name),
            VariableTerm(type)
    )
}

private fun getLambdaContextSystem(
        lambdaDefinition: LambdaDefinition,
        nameGenerator: NameGenerator
): List<AlgebraicEquation> {
    val (name, expr) = lambdaDefinition
    val (type, system) = expr.getEquations(nameGenerator)
    return system + AlgebraicEquation(
            VariableTerm(name),
            VariableTerm(type)
    )
}

private fun TypeExpression.getEquations(
        nameGenerator: NameGenerator
): Pair<TypeName, List<AlgebraicEquation>> = when (this) {
    is TypeLiteral -> {
        val resultType = nameGenerator.next()
        resultType to listOf(AlgebraicEquation(
                VariableTerm(name),
                VariableTerm(resultType)
        ))
    }
    is TypeSum -> {
        val resultType = nameGenerator.next()
        val operands = operands.map { it.getEquations(nameGenerator) }
        val resultNames = operands.map { it.first }
        val equations = operands.flatMap { it.second }
        resultType to equations + resultNames.map {
            AlgebraicEquation(
                    VariableTerm(it),
                    VariableTerm(resultType)
            )
        }
    }
    is TypeProduct -> {
        val resultType = nameGenerator.next()
        val operands = operands.map { it.getEquations(nameGenerator) }
        val resultNames = operands.map { it.first }
        val equations = operands.flatMap { it.second }
        val term = resultNames.foldRight<String, AlgebraicTerm>(VariableTerm(resultType), { typeName, res ->
            FunctionTerm(
                    FUNCTION_SIGN,
                    listOf(VariableTerm(typeName), res)
            )
        })
        resultType to equations + listOf(AlgebraicEquation(
                VariableTerm(name),
                term
        ))
    }
}

private fun LambdaExpression.getEquations(
        nameGenerator: NameGenerator
): Pair<TypeName, List<AlgebraicEquation>> = when (this) {
    is LambdaLiteral -> {
        val resultType = nameGenerator.next()
        resultType to listOf(AlgebraicEquation(
                VariableTerm(name),
                VariableTerm(resultType)
        ))
    }
    is LambdaAbstraction -> {
        val resultType = nameGenerator.next()
        val (type, equations) = expression.getEquations(nameGenerator)
        val term = arguments.foldRight<String, AlgebraicTerm>(VariableTerm(type), { name, res ->
            FunctionTerm(
                    FUNCTION_SIGN,
                    listOf(VariableTerm(name), res)
            )
        })
        resultType to equations + listOf(AlgebraicEquation(
                VariableTerm(resultType),
                term
        ))
    }
    is LambdaApplication -> {
        val resultType = nameGenerator.next()
        val (funcType, funcEq) = function.getEquations(nameGenerator)
        val argumentsEq = arguments.map { it.getEquations(nameGenerator) }
        val types = argumentsEq.map { it.first }
        val equations = argumentsEq.flatMap { it.second }
        val term = types.foldRight<String, AlgebraicTerm>(VariableTerm(resultType), { type, res ->
            FunctionTerm(
                    FUNCTION_SIGN,
                    listOf(VariableTerm(type), res)
            )
        })
        resultType to equations + funcEq + listOf(AlgebraicEquation(
                VariableTerm(funcType),
                term
        ))
    }
}

private fun AlgebraicTerm.toSimpleType(): SimpleType = when (this) {
    is VariableTerm -> TypeTerminal(name)
    is FunctionTerm -> TypeFunction(
            arguments.first().toSimpleType(),
            arguments.last().toSimpleType()
    )
}