package thesis

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.preprocess.ast.toAst
import thesis.preprocess.types.inferSimpleType


fun main(args: Array<String>) {
    val ast = LambdaProgramParser(CommonTokenStream(LambdaProgramLexer(ANTLRInputStream(
            """
                @type Bar = Z | T | Q
                @type Foo = MkFoo Bar
                @type Zap = MkZap Foo Foo Bar

                foo = \ x . \ y . (x y (MkZap (MkFoo T) (MkFoo Q) Z))
                fst = \ x y z. x
                f = \x y . (foo (fst x y x))
                """
    )
    ))).program().toAst()
    println(ast)
    val type = ast.inferSimpleType()
    println(type)
}