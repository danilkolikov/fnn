package thesis.pytorch

import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.algebraic.type.AlgebraicType
import thesis.preprocess.results.*
import thesis.preprocess.spec.DataPointer
import thesis.preprocess.spec.parametrised.Polymorphic
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
            +"from .runtime.modules import ConstantLayer, VariableLayer, AnonymousNetLayer, ConstructorLayer, GuardedLayer, \\"
            +"    ApplicationLayer, RecursiveLayer, TrainableLayer"
            +"from .runtime.data import DataPointer"
            +"from .runtime.types import TypeSpec, LitSpec, RecSpec, VarSpec, ProdSpec"
            +"from .runtime.patterns import VarPattern, LitPattern, ConstructorPattern"
            +""
            if (!data.types.isEmpty()) {
                +""
                +"# Defined Types"
                data.types.forEach { name, signature, type ->
                    -"${getTypeSpecName(name, signature)} = "
                    writePythonType(type)
                    +""
                    +""
                }
            }
            if (!data.expressions.isEmpty()) {
                +""
                +"# Defined Nets"
                data.expressions.forEach { name, type, instance ->
                    -"${getNetworkName(name, type)} = "
                    writePythonExpression(instance)
                    +""
                    +""
                }
                +""
            }
        }
    }

    private fun IndentedWriter.writePythonType(spec: Polymorphic<AlgebraicType>): IndentedWriter {
        when (spec) {
            is Polymorphic.Base -> {
                val type = spec.item
                if (type.recursive) {
                    +"RecSpec("
                    indent {
                        +"name='${type.name}',"
                        +"definition=lambda ${getTypeSpecName(spec.name)}: "
                        indent {
                            writeNotRecursive(type)
                        }
                        +""
                    }
                    -")"
                } else {
                    writeNotRecursive(type)
                }

            }
            is Polymorphic.Instance -> {
                -"${getTypeSpecName(spec.base.name)}.instantiate(${spec.parameters.toPython()})"
            }
        }
        return this
    }

    private fun IndentedWriter.writePythonExpression(spec: Polymorphic<TypedSpec>): IndentedWriter {
        when (spec) {
            is Polymorphic.Base -> {
                writePython(spec.item)
            }
            is Polymorphic.Instance -> {
                -"${getNetworkName(spec.base.name)}.instantiate(${spec.parameters.toPython()})"
            }
        }
        return this
    }

    private fun IndentedWriter.writeNotRecursive(spec: AlgebraicType): IndentedWriter {
        +"TypeSpec(operands=["
        indent {
            spec.structure.operands.forEach { operand ->
                when (operand) {
                    is AlgebraicType.Structure.SumOperand.Object -> +"LitSpec(),"
                    is AlgebraicType.Structure.SumOperand.Product -> {
                        +"ProdSpec(operands=["
                        indent {
                            operand.operands.forEach {
                                when (it) {
                                    is AlgebraicType.Structure.ProductOperand.Variable -> {
                                        +"VarSpec('${it.name}'),"
                                    }
                                    is AlgebraicType.Structure.ProductOperand.Application -> {
                                        val name = getTypeSpecName(it.type.signature, it.arguments.map { it.toSignature() })
                                        +"$name,"
                                    }
                                }
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
            val typeName = getTypeSpecName(spec.toType.signature, spec.toType.typeSignature)
            -"ConstantLayer(type_spec=$typeName, position=${spec.position})"
        }
        is TypedSpec.Variable.Object -> {
            -"VariableLayer.Data(${spec.position})"
        }
        is TypedSpec.Variable.Function -> {
            -"VariableLayer.Net(${spec.position})"
        }
        is TypedSpec.Variable.External -> {
            -"VariableLayer.External(${getNetworkName(spec.signature, spec.typeSignature)})"
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
                appendLnWithoutIndent(", ${spec.toType.toPythonObject()}, ${spec.closurePointer.toPython()}")
            }
            -")"
        }
        is TypedSpec.Function.Trainable -> {
            val parametrisedTrainableSpec = spec.trainableTypedSpec
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
            -"TrainableLayer(${args.joinToString(", ")})"
        }
        is TypedSpec.Function.Constructor -> {
            val toType = getTypeSpecName(spec.toType.signature, spec.toType.typeSignature)
            -"ConstructorLayer(to_type=$toType, position=${spec.position})"
        }
        is TypedSpec.Function.Guarded -> {
            val toType = spec.toType.toPythonObject()
            +"GuardedLayer(to_type=$toType, cases=["
            indent {
                spec.cases.forEach {
                    writePython(it)
                    appendLnWithoutIndent(",")
                }
            }
            -"])"
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
            instanceSignature: InstanceSignature,
            signature: TypeSignature
    ) = (instanceSignature + signature.map { it.toPython() }).joinToString("_")

    private fun getTypeSpecName(instanceName: InstanceName) =
            getTypeSpecName(instanceName.signature, instanceName.typeSignature)

    private fun getNetworkName(
            instanceSignature: InstanceSignature,
            typeSignature: TypeSignature
    ) = "${(instanceSignature + typeSignature.map { it.toPython() })
            .joinToString("_")}_net"

    private fun getNetworkName(
            instanceName: InstanceName
    ) = getNetworkName(instanceName.signature, instanceName.typeSignature)

    private fun TypeSig.toPython(): String = when (this) {
        is TypeSig.Variable -> name
        is TypeSig.Function -> "_${from.toPython()}_to_${to.toPython()}_"
        is TypeSig.Application -> getTypeSpecName(name)
    }

    private fun Map<TypeVariableName, TypeSig>.toPython() = map { (varName, typeName) ->
        "$varName=${typeName.toPythonObject()}"
    }.joinToString(", ")

    private fun TypeSig.toPythonObject() = when (this) {
        is TypeSig.Application -> getTypeSpecName(name)
        is TypeSig.Variable -> "VarSpec('$name')"
        is TypeSig.Function -> throw IllegalArgumentException(
                "Can't instantiate $this as instantiation by functions is unsupported"
        )
    }

    private fun DataPointer.toPython() = "DataPointer($dataOffset, $functionsCount)"
}