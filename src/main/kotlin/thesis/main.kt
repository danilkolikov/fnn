package thesis

import thesis.eval.DataBag
import thesis.eval.EvalSpecCompiler
import thesis.preprocess.ast.ExpressionSorter
import thesis.preprocess.ast.ReaderParser
import thesis.preprocess.memory.TypeMemoryProcessor
import thesis.preprocess.renaming.ExpressionRenamingProcessor
import thesis.preprocess.renaming.NameGenerator
import thesis.preprocess.spec.SpecCompiler
import thesis.preprocess.types.TypeInferenceProcessor
import thesis.pytorch.PyTorchWriter
import java.io.FileReader
import java.io.FileWriter


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

        val specs = SpecCompiler().process(memory)
        println(specs)

//        val compiled = EvalSpecCompiler().process(specs)
//        println(?.eval(DataBag.EMPTY))
        FileWriter("src/main/python/test.py").use {
            PyTorchWriter.writeSpecToFile(it, specs)
        }
    }
}