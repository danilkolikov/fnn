package thesis.pytorch

import thesis.preprocess.spec.DataPattern
import thesis.preprocess.spec.Spec
import thesis.preprocess.spec.TypeSpec
import java.io.FileWriter

/**
 * Writes specs to python
 *
 * @author Danil Kolikov
 */
object PyTorchWriter {
    private const val INDENT = "    "

    fun writeSpecToFile(writer: FileWriter, specs: List<Spec.Function.Guarded>) {
        writer.appendln("""
            |import torch
            |from runtime.modules import ConstantLayer, VariableLayer, AnonymousNetLayer, ConstructorLayer, GuardedLayer, ApplicationLayer
            |from runtime.data import DataPointer
            |from runtime.types import LitSpec, SumSpec, ProdSpec
            |from runtime.patterns import ObjectPattern, VariablePattern
            """.trimMargin("|")
        ).appendln()
        specs.forEach {
            writer.appendln(
                    "${it.name}_net = ${it.toPython()}"
            ).appendln()
        }
    }

    private fun Spec.toPython(): String = when (this) {
        is Spec.Object -> {
            // TODO: Save values to npy files
            "ConstantLayer(torch.Tensor($data))"
        }
        is Spec.Variable.Object -> {
            "VariableLayer.Data(${positions.first}, ${positions.second})"
        }
        is Spec.Variable.Function -> {
            "VariableLayer.Net($position)"
        }
        is Spec.Variable.External -> {
            "VariableLayer.External(${name}_net)"
        }
        is Spec.Function.Anonymous -> "AnonymousNetLayer(${body.toPython()}, " +
                "DataPointer(${closurePointer.dataOffset}, ${closurePointer.functionsCount}))"
        is Spec.Function.Constructor -> "ConstructorLayer($fromTypeSize, $toTypeSize, $offset)"
        is Spec.Function.Guarded ->
            "GuardedLayer([${cases.joinToString(", ") { it.toPython() }}])"
        is Spec.Application -> "ApplicationLayer([${operands.joinToString(", ") { it.toPython() }}], " +
                "$call, $constants, $data, $functions)"
    }

    private fun Spec.Function.Guarded.Case.toPython(): String =
            "GuardedLayer.Case([${patterns.joinToString(", ") { it.toPython() }}], ${body.toPython()})"

    private fun DataPattern.toPython(): String = when (this) {
        is DataPattern.Object -> "ObjectPattern(\"$name\", $position)"
        is DataPattern.Variable -> "VariablePattern(\"$name\", ${type.toPython()}, $start, $end)"
    }

    private fun TypeSpec.toPython(): String = when (this) {
        is TypeSpec.Literal -> "LitSpec(\"$name\", $start)"
        is TypeSpec.Sum ->
            "SumSpec([${operands.joinToString(", ") { it.toPython() }}], $start, $end)"
        is TypeSpec.Product ->
            "ProdSpec(\"$name\", [${operands.joinToString(", ") { it.toPython() }}], $start, $end)"
    }
}