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
}

class NoOpPrettyPrinter : PrettyPrinter {

    override fun print(inferredExpressions: InferredExpressions) {
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
}