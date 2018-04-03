package thesis.pytorch

import java.io.Writer

/**
 * Wrapper around Writer that adds support for indentation
 *
 * @author Danil Kolikov
 */
class IndentedWriter(
        private val writer: Writer,
        private val indent: String
) {
    private var indentation = 0

    private fun appendIndent() {
        for (i in 0 until indentation) {
            writer.append(indent)
        }
    }

    fun append(line: String): IndentedWriter {
        appendIndent()
        writer.append(line)
        return this
    }

    fun appendLnWithoutIndent(line: String): IndentedWriter {
        writer.appendln(line)
        return this
    }

    fun writeln(line: String): IndentedWriter {
        appendIndent()
        writer.appendln(line)
        return this
    }

    operator fun String.unaryPlus() = writeln(this)

    operator fun String.unaryMinus() = append(this)

    fun <T> write(action: IndentedWriter.() -> T) = action(this)

    fun <T> indent(action: IndentedWriter.() -> T): T {
        indentation++
        val res = write(action)
        indentation--
        return res
    }
}
