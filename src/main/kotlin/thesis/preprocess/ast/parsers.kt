package thesis.preprocess.ast

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.LambdaProgramLexer
import thesis.LambdaProgramParser
import thesis.preprocess.Processor
import java.io.InputStream
import java.io.Reader

/**
 * Parser for lambda programs
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

class StringParser : Processor<String, LambdaProgram> {
    override fun process(data: String) = BaseParser().process(ANTLRInputStream(data))
}

class StreamParser : Processor<InputStream, LambdaProgram> {
    override fun process(data: InputStream) = BaseParser().process(ANTLRInputStream(data))
}

class ReaderParser : Processor<Reader, LambdaProgram> {
    override fun process(data: Reader) = BaseParser().process(ANTLRInputStream(data))
}