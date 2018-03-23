package thesis.preprocess.renaming

import thesis.preprocess.Processor
import thesis.preprocess.expressions.Lambda
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.LambdaWithPatterns
import thesis.preprocess.expressions.Pattern
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
        val patternNameMap = mutableMapOf<String, Pattern>()
        val nameMap = mutableMapOf<String, Lambda>()
        val renamed = data.expressions.map { (patterns, lambda) ->
            val renameMap = mutableMapOf<LambdaName, String>()
            val newPatterns = patterns.map { it.rename(renameMap, nameGenerator, patternNameMap) }
            val newLambda = lambda.rename(renameMap, nameMap, nameGenerator)
            LambdaWithPatterns(newPatterns, newLambda)
        }
        return RenamedLambdaImpl(
                SimpleLambdaImpl(data.name, renamed),
                nameMap,
                patternNameMap
        )
    }

    private fun Pattern.rename(
            renameMap: MutableMap<LambdaName, String>,
            nameGenerator: NameGenerator,
            nameMap: MutableMap<String, Pattern>
    ): Pattern = when (this) {
        is Pattern.Object -> this
        is Pattern.Variable -> {
            val newName = nameGenerator.next(name)
            renameMap[name] = newName
            nameMap[newName] = this
            Pattern.Variable(newName)
        }
        is Pattern.Constructor -> Pattern.Constructor(
                name,
                arguments.map { it.rename(renameMap, nameGenerator, nameMap) }
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