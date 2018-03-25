package thesis

import thesis.preprocess.ast.ExpressionSorter
import thesis.preprocess.ast.ReaderParser
import thesis.preprocess.lambda.raw.RawArguments
import thesis.preprocess.lambda.raw.RawLambdaCompiler
import thesis.preprocess.memory.TypeMemoryProcessor
import thesis.preprocess.renaming.ExpressionRenamingProcessor
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.types.TypeInferenceProcessor
import java.io.FileReader


fun main(args: Array<String>) {
    FileReader("src/main/fnn/test.fnn").use {
        val ast = ReaderParser().process(it)
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

        val compiled = RawLambdaCompiler(nameGenerator).process(memory)
        println(compiled["main"]?.call(RawArguments.EMPTY))
    }
}