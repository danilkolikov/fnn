package thesis

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.preprocess.ast.toAst
import thesis.preprocess.types.inferType


fun main(args: Array<String>) {
    val ast = LambdaProgramParser(CommonTokenStream(LambdaProgramLexer(ANTLRInputStream(
            """
                @type Bar = Z | T | Q
                @type Foo = MkFoo Bar
                @type Zap = MkZap Foo Foo Bar

                foo = \ x . \ y . MkFoo (x (MkFoo y) (MkZap (MkFoo T) (MkFoo Q) Z))

                @type bar = Foo -> Zap
                bar = @learn

                @type fst = Foo -> Bar -> Zap -> Foo
                fst = \ x y z. (@learn : Zap -> Foo) (@learn : Zap)
                """
    )
    ))).program().toAst()
    val inferenceContext = ast.inferType()
    println(inferenceContext.types)
}