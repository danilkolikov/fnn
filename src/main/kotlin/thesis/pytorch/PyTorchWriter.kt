package thesis.pytorch

import thesis.preprocess.expressions.type.raw.RawType
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.Specs
import thesis.preprocess.results.TypeSignature
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

    fun writeSpecToFile(writer: FileWriter, data: Specs) {
        IndentedWriter(writer, INDENT).write {
            +"import torch"
            +"from runtime.modules import ConstantLayer, VariableLayer, AnonymousNetLayer, ConstructorLayer, GuardedLayer, \\"
            +"    ApplicationLayer, TrainableLayer"
            +"from runtime.data import DataPointer"
            +"from runtime.types import TypeSpec, LitSpec, ExtSpec, ProdSpec"
            +"from runtime.patterns import ObjectPattern, VariablePattern"
            +""
            +""
            +"# Type Specifications"
            data.typeSpecs.forEach { (name, spec) ->
                -"${getTypeSpecName(name)} = "
                writePython(spec)
                +""
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
                                -"ExtSpec(${it.name}, start=$curOffset)"
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
            // TODO: Save values to npy files
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
                appendLnWithoutIndent(",")
                +"DataPointer(${spec.closurePointer.dataOffset}, ${spec.closurePointer.functionsCount})"
            }
            -")"
        }
        is Spec.Function.Trainable -> -"TrainableLayer(${spec.fromTypeSize}, ${getTypeSpecName(spec.toTypeName)})"
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
        is DataPattern.Variable -> -("VariablePattern(\"${pattern.name}\", ${getTypeSpecName(pattern.typeName)}, " +
                "${pattern.start}, ${pattern.end})")
    }

    private fun getTypeSpecName(name: String) = name

    private fun getNetworkName(
            instanceSignature: InstanceSignature,
            typeSignature: TypeSignature
    ) = "${(instanceSignature + typeSignature.map { it.toPython() }).joinToString("_")}_net"

    private fun RawType.toPython(): String = when (this) {
        is RawType.Literal -> name
        is RawType.Variable -> throw IllegalStateException("Variable $name is not instantiated")
        is RawType.Function -> "_${from.toPython()}_to_${to.toPython()}_"
    }
}