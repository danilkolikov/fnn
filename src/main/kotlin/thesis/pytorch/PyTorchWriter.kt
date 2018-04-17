package thesis.pytorch

import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.Specs
import thesis.preprocess.results.TypeSignature
import thesis.preprocess.spec.DataPattern
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.Spec
import thesis.preprocess.spec.TypeSpec
import thesis.preprocess.spec.parametrised.ParametrisedTrainableSpec
import java.io.FileWriter

/**
 * Generates PyTorch code that describes specs
 *
 * @author Danil Kolikov
 */
object PyTorchWriter {

    private const val INDENT = "    "

    fun writeSpecToFile(writer: FileWriter, data: Specs) {
        IndentedWriter(writer, INDENT).write {
            +"import torch"
            +"from .runtime.modules import ConstantLayer, VariableLayer, AnonymousNetLayer, ConstructorLayer, GuardedLayer, \\"
            +"    ApplicationLayer, TrainableLayer, RecursiveLayer"
            +"from .runtime.poly import TrainablePolyNet"
            +"from .runtime.data import DataPointer"
            +"from .runtime.types import TypeSpec, LitSpec, ExtSpec, ProdSpec"
            +"from .runtime.patterns import ObjectPattern, VariablePattern"
            +""
            +""
            +"# Type Specifications"
            data.typeSpecs.forEach { signature, typeSignature, spec ->
                -"${getTypeSpecName(signature, typeSignature)} = "
                writePython(spec)
                +""
                +""
            }
            if (!data.trainable.isEmpty()) {
                +""
                +"# Poly-net specifications"
                data.trainable.forEach { name, instances ->
                    instances.forEachIndexed { index, parametrisedTrainableSpec ->
                        val arguments = parametrisedTrainableSpec.argumentsTypes.joinToString(", ") {
                            it.toPython()
                        }
                        val result = parametrisedTrainableSpec.resultType.joinToString(", ") {
                            it.toPython()
                        }
                        +"${getPolyNetName(name, index)} = TrainablePolyNet([$arguments], [$result])"
                    }
                    +""
                }
                +""
                +"# Update instances - this method should be called after each backprop on net with polymorphic @learn"
                +"def update_instances():"
                indent {
                    data.trainable.forEach { name, instances ->
                        instances.forEachIndexed { index, _ ->
                            +"${getPolyNetName(name, index)}.update_instances()"
                        }
                    }
                }
                +""
            }
            +""
            +"# Net Specifications"
            data.instances.forEach { name, type, instance ->
                -"${getNetworkName(name, type)} = "
                writePython(instance)
                +""
                +""
            }
        }
    }

    private fun IndentedWriter.writePython(spec: TypeSpec): IndentedWriter {
        +"TypeSpec(operands=["
        indent {
            spec.structure.operands.forEach { operand ->
                when (operand) {
                    is TypeSpec.Structure.SumOperand.Object -> -"LitSpec(${operand.start})"
                    is TypeSpec.Structure.SumOperand.Product -> {
                        +"ProdSpec(operands=["
                        indent {
                            var curOffset = 0
                            operand.operands.forEach {
                                -"ExtSpec(${getTypeSpecName(it.signature, it.typeSignature)}, start=$curOffset)"
                                appendLnWithoutIndent(",")
                                curOffset += it.structure.size
                            }
                        }
                        -"], start=${operand.start})"
                    }
                }
                appendLnWithoutIndent(",")
            }
        }
        -"])"
        return this
    }

    private fun IndentedWriter.writePython(spec: Spec): IndentedWriter = when (spec) {
        is Spec.Object -> {
            -"ConstantLayer(size=${spec.size}, position=${spec.position})"
        }
        is Spec.Variable.Object -> {
            -"VariableLayer.Data(${spec.positions.first}, ${spec.positions.second})"
        }
        is Spec.Variable.Function -> {
            -"VariableLayer.Net(${spec.position})"
        }
        is Spec.Variable.External -> {
            -"VariableLayer.External(${getNetworkName(spec.signature, spec.typeSignature)})"
        }
        is Spec.Function.Anonymous -> {
            +"AnonymousNetLayer("
            indent {
                writePython(spec.body)
                appendLnWithoutIndent(", ")
                +spec.closurePointer.toPython()
            }
            -")"
        }
        is Spec.Function.Recursive -> {
            +"RecursiveLayer("
            indent {
                writePython(spec.body)
                appendLnWithoutIndent(", ${spec.closurePointer.toPython()}")
            }
            -")"
        }
        is Spec.Function.Trainable -> {
            val polyName = getPolyNetName(spec.instanceSignature, spec.instancePosition)
            val args = mutableListOf<String>()
            if (!spec.typeParamsSize.isEmpty()) {
                args.add("type_params={${spec.typeParamsSize.entries.joinToString(", ") {
                    "'${it.key}': ${it.value}"
                }}}")
            }
            -"$polyName.instantiate(${args.joinToString(", ")})"
        }
        is Spec.Function.Constructor -> -"ConstructorLayer(${spec.fromTypeSize}, ${spec.toTypeSize}, ${spec.offset})"
        is Spec.Function.Guarded -> {
            +"GuardedLayer(cases=["
            indent {
                spec.cases.forEach {
                    writePython(it)
                    appendLnWithoutIndent(",")
                }
            }
            -"])"
        }
        is Spec.Application -> {
            +"ApplicationLayer("
            indent {
                +"operands=["
                indent {
                    spec.operands.forEach {
                        writePython(it)
                        appendLnWithoutIndent(",")
                    }
                }
                -"], "
                if (!spec.call.isEmpty()) {
                    appendWithoutIndent("call=${spec.call}, ")
                }
                if (!spec.constants.isEmpty()) {
                    appendWithoutIndent("constants=${spec.constants}, ")
                }
                if (!spec.data.isEmpty()) {
                    appendWithoutIndent("data=${spec.data}, ")
                }
                if (!spec.functions.isEmpty()) {
                    appendWithoutIndent("nets=${spec.functions}")
                }
                +""
            }
            -")"
        }
    }

    private fun IndentedWriter.writePython(case: Spec.Function.Guarded.Case): IndentedWriter = write {
        +"GuardedLayer.Case("
        indent {
            if (case.patterns.isEmpty()) {
                +"[],"
            } else {
                +"["
                indent {
                    case.patterns.forEach {
                        writePython(it)
                        appendLnWithoutIndent(",")
                    }
                }
                +"],"
            }
            writePython(case.body)
            +""
        }
        -")"
    }

    private fun IndentedWriter.writePython(pattern: DataPattern): IndentedWriter = when (pattern) {
        is DataPattern.Object -> -"ObjectPattern(\"${pattern.name}\", ${pattern.position})"
        is DataPattern.Variable -> -("VariablePattern(\"${pattern.name}\", " +
                "${getTypeSpecName(pattern.signature, pattern.typeSignature)}, " +
                "${pattern.start}, ${pattern.end})")
    }

    private fun getTypeSpecName(
            instanceSignature: InstanceSignature,
            signature: TypeSignature
    ) = (instanceSignature + signature.map { it.toPython() }).joinToString("_")

    private fun getNetworkName(
            instanceSignature: InstanceSignature,
            typeSignature: TypeSignature
    ) = "${(instanceSignature + typeSignature.map { it.toPython() }).joinToString("_")}_net"

    private fun getPolyNetName(
            instanceSignature: InstanceSignature,
            instancePosition: Int
    ) = "${instanceSignature.joinToString("_")}_${instancePosition}_polynet"

    private fun RawType.toPython(): String = when (this) {
        is RawType.Literal -> name
        is RawType.Variable -> throw IllegalStateException("Variable $name is not instantiated")
        is RawType.Function -> "_${from.toPython()}_to_${to.toPython()}_"
        is RawType.Application -> if (args.isEmpty()) name else
            "_${(listOf(name) + args.map { it.toPython() }).joinToString("_")}_"
    }

    private fun ParametrisedTrainableSpec.LayerSpec.toPython(): String = when (this) {
        is ParametrisedTrainableSpec.LayerSpec.Fixed -> size.toString()
        is ParametrisedTrainableSpec.LayerSpec.Variable -> "'$name'"
    }

    private fun DataPointer.toPython() = "DataPointer($dataOffset, $functionsCount)"
}