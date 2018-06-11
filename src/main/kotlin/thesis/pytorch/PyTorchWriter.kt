package thesis.pytorch

import thesis.preprocess.expressions.TypeName
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.results.InstanceSignature
import thesis.preprocess.results.TypedSpecs
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.parametrised.ParametrisedTrainableSpec
import thesis.preprocess.spec.typed.PatternInstance
import thesis.preprocess.spec.typed.TypedSpec
import java.io.FileWriter

/**
 * Generates PyTorch code that describes specs
 *
 * @author Danil Kolikov
 */
object PyTorchWriter {

    private const val INDENT = "    "

    fun writeSpecToFile(writer: FileWriter, data: TypedSpecs) {
        IndentedWriter(writer, INDENT).write {
            +"import torch"
            +"from .runtime import trees"
            +"from .runtime import loss"
            +"from .runtime.modules import ConstantLayer, VariableLayer, AnonymousNetLayer, ConstructorLayer, GuardedLayer, \\"
            +"    ApplicationLayer, RecursiveLayer, TrainableLayer, ZeroLayer"
            +"from .runtime.data import DataPointer, DataBag"
            +"from .runtime.types import TypeSpec, LitSpec, ProdSpec, VarSpec, ExtSpec"
            +"from .runtime.patterns import VarPattern, LitPattern, ConstructorPattern"
            +""
            +"DEFINED_TYPES = {}"
            +"TrainableLayer = TrainableLayer.bind_defined_types(DEFINED_TYPES)"
            +"ZeroLayer = ZeroLayer.bind_defined_types(DEFINED_TYPES)"
            if (!data.types.isEmpty()) {
                +""
                +"# Defined Types"
                +""
                data.types.forEach { name, type ->
                    -"$name = "
                    writePythonType(type)
                    +""
                    +"DEFINED_TYPES['$name'] = $name"
                    +""
                }
            }
            if (!data.expressions.isEmpty()) {
                +""
                +"# Defined Nets"
                data.expressions.forEach { name, instance ->
                    -"${getNetworkName(name)} = "
                    writePython(instance)
                    +""
                    +""
                }
                +""
            }
        }
    }

    private fun IndentedWriter.writePythonType(spec: AlgebraicType): IndentedWriter {
        +"TypeSpec(operands=["
        indent {
            spec.structure.operands.forEach { operand ->
                when (operand) {
                    is AlgebraicType.Structure.SumOperand.Object -> +"LitSpec(),"
                    is AlgebraicType.Structure.SumOperand.Product -> {
                        +"ProdSpec(operands=["
                        indent {
                            operand.operands.forEach {
                                +(it.toType().toPythonObject() + ",")
                            }
                        }
                        +"]),"
                    }
                }
            }
        }
        -"])"
        return this
    }

    private fun IndentedWriter.writePython(spec: TypedSpec): IndentedWriter = when (spec) {
        is TypedSpec.Object -> {
            val typeName = getTypeSpecName(spec.toType.type.name)
            -"ConstantLayer(type_spec=$typeName, position=${spec.position})"
        }
        is TypedSpec.Variable.Object -> {
            -"VariableLayer.Data(${spec.position})"
        }
        is TypedSpec.Variable.Function -> {
            -"VariableLayer.Net(${spec.position})"
        }
        is TypedSpec.Variable.External -> {
            -"VariableLayer.External(${getNetworkName(spec.signature)})"
        }
        is TypedSpec.Function.Anonymous -> {
            +"AnonymousNetLayer("
            indent {
                writePython(spec.body)
                appendLnWithoutIndent(", ")
                +spec.closurePointer.toPython()
            }
            -")"
        }
        is TypedSpec.Function.Recursive -> {
            +"RecursiveLayer("
            indent {
                writePython(spec.body)
                val toType = spec.type.getResultType()

                var lastLine = ", ZeroLayer(${toType.toPythonObject()}), ${spec.closurePointer.toPython()}"
                if (spec.isTailRecursive) {
                    lastLine += ", is_tail_recursive=True"
                }
                appendLnWithoutIndent(lastLine)
            }
            -")"
        }
        is TypedSpec.Function.Trainable -> {
            -spec.toPythonObject()
        }
        is TypedSpec.Function.Constructor -> {
            val toType = getTypeSpecName(spec.toType.type.name)
            -"ConstructorLayer(to_type=$toType, position=${spec.position})"
        }
        is TypedSpec.Function.Guarded -> {
            val toType = spec.type.getResultType()
            +"GuardedLayer("
            indent {
                +"cases=["
                indent {
                    spec.cases.forEach {
                        writePython(it)
                        appendLnWithoutIndent(",")
                    }
                }
                +"],"
                +"mismatch_handler=ZeroLayer(${toType.toPythonObject()}),"
                +"pointer=${spec.closurePointer.toPython()}"
            }
            -")"
        }
        is TypedSpec.Application -> {
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

    private fun IndentedWriter.writePython(case: TypedSpec.Function.Guarded.Case): IndentedWriter = write {
        +"GuardedLayer.Case("
        indent {
            writePython(case.pattern)
            appendLnWithoutIndent(", ")
            writePython(case.body)
            +""
        }
        -")"
    }

    private fun IndentedWriter.writePython(pattern: PatternInstance): IndentedWriter = when (pattern) {
        is PatternInstance.Literal -> -"LitPattern(${pattern.position})"
        is PatternInstance.Variable -> -"VarPattern()"
        is PatternInstance.Constructor -> {
            +"ConstructorPattern(${pattern.position}, operands=["
            indent {
                pattern.operands.forEach {
                    writePython(it)
                    appendLnWithoutIndent(",")
                }
            }
            -"])"
        }
    }

    private fun getTypeSpecName(
            name: TypeName
    ) = name

    private fun getNetworkName(
            instanceSignature: InstanceSignature
    ) = "${instanceSignature.joinToString("_")}_net"

    private fun Map<TypeVariableName, Type>.toPython(): String = map { (varName, typeName) ->
        "$varName=${typeName.toPythonObject()}"
    }.joinToString(", ")

    private fun Type.toPythonObject() = when (this) {
        is Type.Application -> if (args.isEmpty()) "ExtSpec('${type.name}')" else
            "ExtSpec('${type.name}', ${type.parameters.zip(args).toMap().toPython()})"
        is Type.Variable -> "VarSpec('$name')"
        is Type.Function -> throw IllegalArgumentException(
                "Can't instantiate $this as instantiation by functions is unsupported"
        )
    }

    private fun DataPointer.toPython() = "DataPointer($dataOffset, $functionsCount)"

    private fun TypedSpec.Function.Trainable.toPythonObject(): String {
        val parametrisedTrainableSpec = trainableTypedSpec
        val arguments = parametrisedTrainableSpec.arguments.joinToString(", ") {
            it.toPythonObject()
        }
        val result = parametrisedTrainableSpec.toType.toPythonObject()
        val args = mutableListOf<String>()
        args.add("[$arguments]")
        args.add(result)
        parametrisedTrainableSpec.options.entries.map {
            "${it.key}=${it.value}"
        }.forEach { args.add(it) }
        return "TrainableLayer(${args.joinToString(", ")})"
    }
}