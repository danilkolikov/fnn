/**
 * Algorithm for type inference
 */
package thesis.preprocess.types

import thesis.preprocess.ast.Definition
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.ast.LambdaProgramExpression
import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.Expression
import thesis.utils.*

interface TypeInferenceContext {
    val nameGenerator: NameGenerator

    val typeScope: MutableMap<TypeName, AlgebraicTerm>
    val expressionScope: MutableMap<LambdaName, AlgebraicTerm>
}

data class TypeInferenceLocalContext<out C : TypeInferenceContext, out E : Expression>(
        val globalContext: C,
        val definition: Definition<E>,

        val nameMap: Map<String, String>,

        val definedTypes: Set<TypeName>,
        val definedExpressions: Set<LambdaName>,

        val scope: Map<LambdaName, AlgebraicTerm>,

        var solution: Map<VariableTerm, AlgebraicTerm> = mapOf()
)

interface LambdaProgramExpressionProcessor<C : TypeInferenceContext, in E : LambdaProgramExpression> {

    fun processExpression(context: C, expression: E): C
}

abstract class TypeInferenceAlgorithm<C : TypeInferenceContext, E : Expression>
    : LambdaProgramExpressionProcessor<C, Definition<E>> {

    abstract fun Definition<E>.getLocalContext(context: C): TypeInferenceLocalContext<C, E>

    abstract fun E.getEquations(
            localContext: TypeInferenceLocalContext<C, E>
    ): Pair<AlgebraicTerm, List<AlgebraicEquation>>

    abstract fun TypeInferenceLocalContext<C, E>.updateGlobalContext(): C

    override fun processExpression(context: C, expression: Definition<E>): C {
        val localContext = expression.getLocalContext(context)
        val newDefinition = localContext.definition
        val (type, equations) = newDefinition.expression.getEquations(localContext)
        val system = equations + listOf(AlgebraicEquation(
                VariableTerm(newDefinition.name),
                type
        )) + (context.expressionScope[newDefinition.name]?.let {
            // TODO: fix this crutch for type declaration
            listOf(AlgebraicEquation(
                    VariableTerm(newDefinition.name),
                    it
            ))
        } ?: emptyList())

        val solution = solveSystem(system)
        localContext.solution = localContext.renameTypes(solution)
        return localContext.updateGlobalContext()
    }

    private fun TypeInferenceLocalContext<C, E>.renameTypes(
            solution: Map<VariableTerm, AlgebraicTerm>
    ): Map<VariableTerm, AlgebraicTerm> {
        // Types with equal names form undirected graph
        // Find connected components and set human-readable names to types in them
        val edges = mutableListOf<Edge<String>>()
        for ((left, right) in solution) {
            if (right is VariableTerm) {
                edges.add(Edge(left.name, right.name))
            }
        }
        val graph = UndirectedGraph(edges)
        val components = graph.getConnectedComponents(definedTypes)
        val distinctTypes = definedTypes.map { components[it] }.toSet()
        if (distinctTypes.size != definedTypes.size) {
            // Some types were clashed during unification, that's definitely a type error
            throw TypeInferenceError(definition.expression, globalContext)
        }
        val connectedComponents = components
                .filter { (name, value) -> name != value }
                .map { (name, value) ->
                    VariableTerm(name) to VariableTerm(value)
                }
                .toMap()
        return connectedComponents + solution
                .filterKeys {
                    !connectedComponents.containsKey(it) && !definedTypes.contains(it.name)
                }
                .mapValues { (_, value) -> value.replace(connectedComponents) }
    }
}
