package thesis

import thesis.preprocess.ast.ExpressionSorter
import thesis.preprocess.ast.Parser
import thesis.preprocess.lambda.LambdaCompiler
import thesis.preprocess.memory.TypeMemoryProcessor
import thesis.preprocess.renaming.ExpressionRenamingProcessor
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.types.TypeInferenceProcessor


fun main(args: Array<String>) {
    val ast = Parser().process(
            """
                @type Bar = Z | T | Q
                @type Foo = MkFoo Bar
                @type Zap = MkZap Foo Foo Bar

                foo = \ x . \ y . MkFoo (x (MkFoo y) (MkZap (MkFoo T) (MkFoo Q) Z))

                @type bar = Foo -> Zap
                bar = @learn

                @type fst = Bar -> Bar -> Bar -> Bar
                fst = \ x y z. x

                test = fst (fst (fst Q T Z) T Q) Z (fst Q Q Q)
                """
    )
    println(ast)

    val sorted = ExpressionSorter().process(ast)
    println(sorted)

    val nameGenerator = NameGenerator()
    val renamed = ExpressionRenamingProcessor(nameGenerator).process(sorted)
    println(renamed)

    val inferred = TypeInferenceProcessor(nameGenerator).process(renamed)
    println(inferred)

    val memory = TypeMemoryProcessor().process(inferred)
    println(memory)

    val compiled = LambdaCompiler().process(memory)
    println(compiled["test"]?.get(0)?.execute())
}