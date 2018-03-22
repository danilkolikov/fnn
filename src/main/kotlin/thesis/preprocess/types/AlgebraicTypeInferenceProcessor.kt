package thesis.preprocess.types

import thesis.preprocess.Processor
import thesis.preprocess.expressions.AlgebraicType
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.results.InferredType
import thesis.preprocess.results.InferredTypeImpl
import thesis.preprocess.results.RenamedType
import thesis.utils.AlgebraicEquation
import thesis.utils.AlgebraicTerm
import thesis.utils.FunctionTerm
import thesis.utils.VariableTerm

/**
 * Infers type information for algebraic types
 *
 * @author Danil Kolikov
 */
class AlgebraicTypeInferenceProcessor(
        private val nameGenerator: NameGenerator
) : Processor<Map<TypeName, RenamedType>, Map<TypeName, InferredType>> {

    override fun process(data: Map<TypeName, RenamedType>): Map<TypeName, InferredType> {
        val result = mutableMapOf<TypeName, InferredType>()
        data.forEach { name, expression ->
            val scope = (result.keys + expression.type.getConstructors())
                    .map { it to VariableTerm(it) }
                    .toMap()
            val (resultType, equations) = expression.type.getEquations(scope)
            val system = equations + listOf(AlgebraicEquation(VariableTerm(name), resultType))
            val solution = system.inferTypes(
                    result.keys + setOf(name)
            )

            val typeConstructors = expression.type.getConstructors().map {
                val type = solution[it] ?: throw TypeInferenceError(expression.type)
                it to type.toType()
            }.toMap(LinkedHashMap())

            result[name] = InferredTypeImpl(expression, typeConstructors)
        }
        return result
    }

    private fun AlgebraicType.getEquations(
            scope: Map<String, AlgebraicTerm>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>> {
        val resultType: AlgebraicTerm
        val equations: List<AlgebraicEquation>

        when (this) {
            is AlgebraicType.Literal -> {
                resultType = scope[name]
                        ?: throw UnknownTypeError(name)
                equations = listOf()
            }
            is AlgebraicType.Sum -> {
                resultType = VariableTerm(nameGenerator.next("t"))
                val operands = operands.map { it.getEquations(scope) }
                val resultNames = operands.map { it.first }
                equations = operands.flatMap { it.second } + resultNames.map {
                    AlgebraicEquation(
                            it,
                            resultType
                    )
                }
            }
            is AlgebraicType.Product -> {
                resultType = VariableTerm(nameGenerator.next("t"))
                val constructorType = scope[name]
                        ?: throw UnknownTypeError(name)

                val operands = operands.map { it.getEquations(scope) }
                val resultNames = operands.map { it.first }
                val term = resultNames.foldRight<AlgebraicTerm, AlgebraicTerm>(resultType, { typeName, res ->
                    FunctionTerm(
                            FUNCTION_SIGN,
                            listOf(typeName, res)
                    )
                })
                equations = operands.flatMap { it.second } + listOf(AlgebraicEquation(
                        constructorType,
                        term
                ))
            }
        }
        return resultType to equations
    }
}