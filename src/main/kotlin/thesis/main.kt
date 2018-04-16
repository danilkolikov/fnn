package thesis

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import thesis.eval.DataBag
import thesis.eval.EvalSpecCompiler
import thesis.preprocess.ast.ExpressionSorter
import thesis.preprocess.ast.ReaderParser
import thesis.preprocess.spec.SpecCompiler
import thesis.preprocess.spec.parametrised.ParametrisedSpecCompiler
import thesis.preprocess.types.TypeInferenceProcessor
import thesis.pytorch.PyTorchWriter
import thesis.utils.NameGenerator
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths


fun main(args: Array<String>) = mainBody {
    val parsedArgs = ArgParser(args).parseInto(::CompilerArgs)
    parsedArgs.run {
        val outputDirectory = Paths.get(directory)
        val printer = if (verbose) ConsolePrettyPrinter() else NoOpPrettyPrinter()
        input.forEach { file ->
            FileReader(file).use {
                val ast = ReaderParser().process(it)
                val sorted = ExpressionSorter().process(ast)
                val nameGenerator = NameGenerator()
                val inferred = TypeInferenceProcessor(nameGenerator).process(sorted)
                printer.print(inferred)

                val parametrisedSpec = ParametrisedSpecCompiler().process(inferred)
                printer.print(parametrisedSpec)

                val specs = SpecCompiler().process(parametrisedSpec)

                when (mode) {
                    CompilerMode.COMPILE -> {
                        val filePath = Paths.get(file)
                        val fileName = filePath.fileName
                        val outputFileName = fileName.toString().replaceAfterLast(".", "py")
                        val outputFile = outputDirectory.resolve(outputFileName)

                        Files.createDirectories(outputDirectory)
                        FileWriter(outputFile.toFile()).use {
                            PyTorchWriter.writeSpecToFile(it, specs)
                        }
                    }
                    CompilerMode.EXECUTE -> {
                        val compiled = EvalSpecCompiler().process(specs)
                        compiled[listOf("main"), emptyList()]?.eval(DataBag.EMPTY)?.let {
                            println(it)
                        }
                    }
                }
            }
        }
    }
}