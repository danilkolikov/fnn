package thesis.eval

import thesis.preprocess.Processor
import thesis.preprocess.results.Instances
import thesis.preprocess.results.Specs
import thesis.preprocess.spec.Spec
import thesis.preprocess.types.UnknownExpressionError

/**
 * Compiles specs to executable versions
 *
 * @author Danil Kolikov
 */
class EvalSpecCompiler : Processor<Specs, Instances<EvalSpec>> {

    override fun process(data: Specs): Instances<EvalSpec> {
        val result = Instances<EvalSpec>()
        data.instances.forEach { signature, type, spec ->
            val evalSpec = spec.toEvalSpec(result) as EvalSpec.Function.Guarded
            result[signature, type] = evalSpec
        }
        return result
    }

    private fun Spec.toEvalSpec(processed: Instances<EvalSpec>): EvalSpec = when (this) {
        is Spec.Variable.Object -> EvalSpec.Variable.Object(this)
        is Spec.Variable.Function -> EvalSpec.Variable.Function(this)
        is Spec.Variable.External -> EvalSpec.Variable.External(
                this,
                processed[signature, typeSignature] ?: throw UnknownExpressionError(
                        signature.toString()
                )
        )
        is Spec.Object -> EvalSpec.Object(this)
        is Spec.Function.Trainable -> EvalSpec.Function.Trainable(this)
        is Spec.Function.Constructor -> EvalSpec.Function.Constructor(this)
        is Spec.Function.Guarded -> EvalSpec.Function.Guarded(
                this,
                cases.map { EvalSpec.Function.Guarded.Case(it, it.body.toEvalSpec(processed)) }
        )
        is Spec.Function.Anonymous -> EvalSpec.Function.Anonymous(
                this,
                body.toEvalSpec(processed)
        )
        is Spec.Function.Recursive -> EvalSpec.Function.Recursive(
                this,
                body.toEvalSpec(processed)
        )
        is Spec.Application -> EvalSpec.Application(
                this,
                operands.map { it.toEvalSpec(processed) }
        )
    }
}