package thesis

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

/**
 * CLI-arguments for compiler
 *
 * @author Danil Kolikov
 */
class CompilerArgs(parser: ArgParser) {

    val input by parser.positionalList(
            name="INPUT", help="Source files to compile"
    ).default(emptyList())

    val directory by parser.storing(
            "-d", "--dir",
            help = "Output directory"
    ).default(".")

    val verbose by parser.flagging(
            "-v", "--verbose",
            help = "Show verbose output"
    ).default(false)
}