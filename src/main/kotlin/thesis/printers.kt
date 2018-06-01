/**
 * Classes that print info about compilation process
 *
 * @author Danil Kolikov
 */
package thesis

import thesis.preprocess.results.InferredExpressions
import thesis.preprocess.results.ParametrisedSpecs

interface PrettyPrinter {

    fun print(inferredExpressions: InferredExpressions)

    fun print(parametrisedSpecs: ParametrisedSpecs)
}

class NoOpPrettyPrinter : PrettyPrinter {

    override fun print(inferredExpressions: InferredExpressions) {
    }

    override fun print(parametrisedSpecs: ParametrisedSpecs) {
    }
}

class ConsolePrettyPrinter : PrettyPrinter {

    override fun print(inferredExpressions: InferredExpressions) {
        if (!inferredExpressions.typeDefinitions.isEmpty()) {
            println("Defined Types:")
            inferredExpressions.typeDefinitions.forEach { name, type ->
                println(
                        """
                        |    $name:
                        |        Kind: ${type.kind}
                        |        Definition: $type
                        |        Constructors:
                        ${type.constructors.entries.joinToString("\n") {
                            "|            ${it.key} : ${it.value.type}"
                        }}
                        """.trimMargin("|")
                )
            }
            println()
        }
        if (!inferredExpressions.lambdaDefinitions.isEmpty()) {
            println("Defined expressions: ")
            inferredExpressions.lambdaDefinitions.forEach { name, expr ->
                println("    $name : ${expr.type}")
            }
            println()
        }
    }

    override fun print(parametrisedSpecs: ParametrisedSpecs) {
        if (!parametrisedSpecs.types.isEmpty()) {
            println("Type Instances: ")
            parametrisedSpecs.types.forEach { _, params, type ->
                val typeName = if (params.isEmpty()) type.item.name else "${type.item.name} ${params.joinToString(" ")}"
                println("    $typeName")
            }
            println()
        }
        if (!parametrisedSpecs.expressions.isEmpty()) {
            println("Expression instances: ")
            parametrisedSpecs.expressions.forEach { names, _, expression ->
                val name = "${names.joinToString("→")} : ${expression.item.type}"
                println("    $name")
            }
            println()
        }
    }
}