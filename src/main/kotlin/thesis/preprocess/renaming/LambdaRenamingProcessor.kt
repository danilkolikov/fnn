package thesis.preprocess.renaming

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.results.RenamedLambda
import thesis.preprocess.results.RenamedLambdaImpl
import thesis.preprocess.results.SimpleLambda
import thesis.preprocess.results.SimpleLambdaImpl

/**
 * Renames bound variables in lambda-expressions
 *
 * @author Danil Kolikov
 */
class LambdaRenamingProcessor(
        private val nameGenerator: NameGenerator
) : Processor<SimpleLambda, RenamedLambda> {

    override fun process(data: SimpleLambda): RenamedLambda {
        val nameMap = mutableMapOf<String, Lambda>()
        val renamed = data.expressions.map { it.rename(emptyMap(), nameMap, nameGenerator) }
        return RenamedLambdaImpl(
                SimpleLambdaImpl(renamed),
                nameMap
        )
    }

    private fun Lambda.rename(
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
                expression.rename(renameMap, nameMap, nameGenerator),
                type
        )
        is Lambda.Application -> Lambda.Application(
                function.rename(renameMap, nameMap, nameGenerator),
                arguments.map { it.rename(renameMap, nameMap, nameGenerator) }
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
                    expression.rename(renameMap + newNames, nameMap, nameGenerator)
            )
        }
    }

    companion object {
        private const val LEARN_PREFIX = "learn"
    }
}