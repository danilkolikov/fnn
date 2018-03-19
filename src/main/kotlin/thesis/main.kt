package thesis

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import thesis.preprocess.Preprocessor
import thesis.preprocess.ast.toAst
import thesis.preprocess.lambda.LambdaCompiler
import thesis.preprocess.typeinfo.TypeInfoExtractor


fun main(args: Array<String>) {
    val ast = LambdaProgramParser(CommonTokenStream(LambdaProgramLexer(ANTLRInputStream(
            """
                @type Bar = Z | T | Q
                @type Foo = MkFoo Bar
                @type Zap = MkZap Foo Foo Bar

                foo = \ x . \ y . MkFoo (x (MkFoo y) (MkZap (MkFoo T) (MkFoo Q) Z))

                @type bar = Foo -> Zap
                bar = @learn

                @type fst = Bar -> Bar -> Bar -> Bar
                fst = \ x y z. x

                test = fst (fst (fst Q T Z) T Q) Z (fst Q Q Q)
                """
    )
    ))).program().toAst()
    val typeInfoExtractor = TypeInfoExtractor()
    val lambdaCompiler = LambdaCompiler(typeInfoExtractor.context)
    val preprocessor = Preprocessor(listOf(typeInfoExtractor, lambdaCompiler))

    val inferenceContext = preprocessor.process(ast)
    println(inferenceContext.types)

    println(typeInfoExtractor.context)
    println(lambdaCompiler.context)
}