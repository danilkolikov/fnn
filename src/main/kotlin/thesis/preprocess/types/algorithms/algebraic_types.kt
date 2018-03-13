/**
 * Inference algorithm for algebraic types.
 * Infers types of constructors and extracts definitions
 */
package thesis.preprocess.types.algorithms

import thesis.preprocess.ast.Definition
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.types.*
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

interface AlgebraicTypeContext : TypeContext {

    val typeDefinitions: MutableMap<TypeName, AlgebraicType>

    val typeConstructors: MutableMap<TypeName, AlgebraicTerm>

    val typeScope: MutableMap<TypeName, AlgebraicTerm>
}

data class AlgebraicTypeLocalContext(
        override val expression: Definition<AlgebraicType>,
        override val typeScope: Set<String>,
        override val scope: Map<String, AlgebraicTerm>,
        override var solution: Map<VariableTerm, AlgebraicTerm> = mapOf()
) : LocalTypeContext<Definition<AlgebraicType>>

class AlgebraicTypeInferenceAlgorithm(
        override val typeContext: AlgebraicTypeContext
) : TypeInferenceAlgorithm<AlgebraicTypeContext, AlgebraicType, AlgebraicTypeLocalContext>() {

    override fun getLocalContext(expression: Definition<AlgebraicType>): AlgebraicTypeLocalContext {
        val definedTypes = typeContext.typeScope.keys + setOf(expression.name)
        val scope = typeContext.typeScope + (expression.expression.getConstructors() - definedTypes)
                .map { it to VariableTerm(typeContext.nameGenerator.next(it)) }
                .toMap()
        return AlgebraicTypeLocalContext(
                expression,
                definedTypes,
                scope
        )
    }

    override fun updateContext(localContext: AlgebraicTypeLocalContext) {
        val definition = localContext.expression

        typeContext.typeDefinitions[definition.name] = definition.expression
        typeContext.typeScope[definition.name] = VariableTerm(definition.name)

        val constructors = definition.expression.getConstructors()
        constructors.forEach {
            val term = localContext.scope[it] ?: throw TypeInferenceError(definition.expression, typeContext)
            val type = localContext.solution[term] ?: throw TypeInferenceError(definition.expression, typeContext)
            typeContext.typeConstructors[it] = type
        }
    }

    private fun AlgebraicType.getConstructors(): Set<String> = when (this) {
        is AlgebraicType.Literal -> setOf(name)
        is AlgebraicType.Product -> setOf(name)
        is AlgebraicType.Sum -> operands.flatMap { it.getConstructors() }.toSet()
    }

    override fun AlgebraicType.getEquations(
            localContext: AlgebraicTypeLocalContext
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> = when (this) {
        is AlgebraicType.Literal -> {
            val resultType = localContext.scope[name]
                    ?: throw UnknownTypeError(name, typeContext)
            resultType to listOf()
        }
        is AlgebraicType.Sum -> {
            val resultType = VariableTerm(typeContext.nameGenerator.next("t"))
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
            val productResult = VariableTerm(typeContext.nameGenerator.next("t"))
            val constructorType = localContext.scope[name]
                    ?: throw UnknownTypeError(name, typeContext)

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