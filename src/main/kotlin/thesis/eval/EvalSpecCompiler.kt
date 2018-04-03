package thesis.eval

import thesis.preprocess.Processor
import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.results.Specs
import thesis.preprocess.spec.Spec
import thesis.preprocess.types.UnknownExpressionError

/**
 * Compiles specs to executable versions
 *
 * @author Danil Kolikov
 */
class EvalSpecCompiler : Processor<Specs, Map<LambdaName, EvalSpec.Function.Guarded>> {

    override fun process(data: Specs): Map<LambdaName, EvalSpec.Function.Guarded> {
        val result = mutableMapOf<LambdaName, EvalSpec.Function.Guarded>()
        data.specs.forEach { spec ->
            val evalSpec = spec.toEvalSpec(result) as EvalSpec.Function.Guarded
            result[spec.name] = evalSpec
        }
        return result
    }

    private fun Spec.toEvalSpec(processed: Map<LambdaName, EvalSpec.Function.Guarded>): EvalSpec = when (this) {
        is Spec.Variable.Object -> EvalSpec.Variable.Object(this)
        is Spec.Variable.Function -> EvalSpec.Variable.Function(this)
        is Spec.Variable.External -> EvalSpec.Variable.External(
                this,
                processed[name] ?: throw UnknownExpressionError(name)
        )
        is Spec.Object -> EvalSpec.Object(this)
        is Spec.Function.Constructor -> EvalSpec.Function.Constructor(this)
        is Spec.Function.Guarded -> EvalSpec.Function.Guarded(
                this,
                cases.map { EvalSpec.Function.Guarded.Case(it, it.body.toEvalSpec(processed)) }
        )
        is Spec.Function.Anonymous -> EvalSpec.Function.Anonymous(
                this,
                body.toEvalSpec(processed)
        )
        is Spec.Application -> EvalSpec.Application(
                this,
                operands.map { it.toEvalSpec(processed) }
        )
    }
}