package thesis.pytorch

import thesis.preprocess.results.Specs
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
            +"from runtime.types import LitSpec, ExtSpec, SumSpec, ProdSpec"
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
            data.specs.forEach {
                -"${getNetworkName(it.name)} = "
                writePython(it)
                +""
                +""
            }
        }
    }

    private fun IndentedWriter.writePython(spec: TypeSpec): IndentedWriter = when (spec) {
        is TypeSpec.Literal -> -"LitSpec(\"${spec.name}\", start=${spec.start})"
        is TypeSpec.External -> -"ExtSpec(${spec.name}, start=${spec.start})"
        is TypeSpec.Sum -> {
            +"SumSpec(["
            indent {
                spec.operands.forEach {
                    writePython(it)
                    appendLnWithoutIndent(",")
                }
            }
            -"], start=${spec.start}, end=${spec.end})"
        }
        is TypeSpec.Product -> {
            +"ProdSpec(\"${spec.name}\", ["
            indent {
                spec.operands.forEach {
                    writePython(it)
                    appendLnWithoutIndent(",")
                }
            }
            -"], start=${spec.start}, end=${spec.end})"
        }
    }

    private fun IndentedWriter.writePython(spec: Spec): IndentedWriter = when (spec) {
        is Spec.Object -> {
            // TODO: Save values to npy files
            -"ConstantLayer(torch.Tensor(${spec.data}))"
        }
        is Spec.Variable.Object -> {
            -"VariableLayer.Data(${spec.positions.first}, ${spec.positions.second})"
        }
        is Spec.Variable.Function -> {
            -"VariableLayer.Net(${spec.position})"
        }
        is Spec.Variable.External -> {
            -"VariableLayer.External(${getNetworkName(spec.name)})"
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

    private fun getNetworkName(name: String) = "${name}_net"
}