package thesis.preprocess.ast

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.LambdaProgramLexer
import thesis.LambdaProgramParser
import thesis.preprocess.Processor
import java.io.InputStream
import java.io.Reader

/**
 * Base Parser for lambda programs
 *
 * @author Danil Kolikov
 */
private class BaseParser : Processor<ANTLRInputStream, LambdaProgram> {
    override fun process(data: ANTLRInputStream) = LambdaProgramParser(
            CommonTokenStream(
                    LambdaProgramLexer(
                            data
                    )
            )
    ).program().toAst()
}

/**
 * Parses lambda program from String input
 *
 * @author Danil Kolikov
 */
class StringParser : Processor<String, LambdaProgram> {

    override fun process(data: String) = BaseParser().process(ANTLRInputStream(data))
}

/**
 * Parses lambda programs from stream input
 *
 * @author Danil Kolikov
 */
class StreamParser : Processor<InputStream, LambdaProgram> {

    override fun process(data: InputStream) = BaseParser().process(ANTLRInputStream(data))
}

/**
 * Parses lambda programs using Reader
 *
 * @author Danil Kolikov
 */
class ReaderParser : Processor<Reader, LambdaProgram> {

    override fun process(data: Reader) = BaseParser().process(ANTLRInputStream(data))
}