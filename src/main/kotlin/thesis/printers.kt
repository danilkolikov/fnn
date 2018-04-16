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
                            "|            ${it.key} : ${it.value}"
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
        if (!parametrisedSpecs.typeInstances.isEmpty()) {
            println("Type Instances: ")
            parametrisedSpecs.typeInstances.forEach { _, params, type ->
                val typeName = if (params.isEmpty()) type.name else "${type.name} ${params.joinToString(" ")}"
                println("    $typeName")
            }
            println()
        }
        if (!parametrisedSpecs.instances.isEmpty()) {
            println("Expression instances: ")
            parametrisedSpecs.instances.forEach { names, _, expression ->
                if (!expression.isInstantiated()) {
                    return@forEach
                }
                val name = "${names.joinToString("→")} : ${expression.type}"
                println("    $name")
            }
            println()
        }
        if (!parametrisedSpecs.trainable.isEmpty()) {
            println("@learn instances: ")
            parametrisedSpecs.trainable.forEach { names, specs ->
                specs.forEach { spec ->
                    val name = "${names.joinToString("→")} : ${spec.type}"
                    println("    $name")
                }
            }
            println()
        }
        println()
    }
}