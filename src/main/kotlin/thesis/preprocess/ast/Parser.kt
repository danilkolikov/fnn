package thesis.preprocess.ast

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.LambdaProgramLexer
import thesis.LambdaProgramParser
import thesis.preprocess.Processor

/**
 * Parser for lambda programs
 *
 * @author Danil Kolikov
 */
class Parser : Processor<String, LambdaProgram> {
    override fun process(data: String) = LambdaProgramParser(
            CommonTokenStream(
                    LambdaProgramLexer(
                            ANTLRInputStream(
                                    data
                            )
                    )
            )
    ).program().toAst()
}