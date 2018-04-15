package thesis

import thesis.preprocess.ast.ExpressionSorter
import thesis.preprocess.ast.ReaderParser
import thesis.preprocess.spec.parametrised.ParametrisedSpecCompiler
import thesis.utils.NameGenerator
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

        val inferred = TypeInferenceProcessor(nameGenerator).process(sorted)
        println(inferred)

        val parametrisedSpec = ParametrisedSpecCompiler().process(inferred)
        println(parametrisedSpec)

        val specs = SpecCompiler().process(parametrisedSpec)
        println(specs)

//        val compiled = EvalSpecCompiler().process(specs)
//        println(?.eval(DataBag.EMPTY))
        FileWriter("src/main/python/test.py").use {
            PyTorchWriter.writeSpecToFile(it, specs)
        }
    }
}