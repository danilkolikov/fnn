/**
 * Objects representing names of polymorphic types and expressions
 *
 * @author Danil Kolikov
 */
package thesis.preprocess.results

import thesis.preprocess.expressions.LambdaName
import thesis.preprocess.expressions.Replaceable
import thesis.preprocess.expressions.TypeVariableName
import thesis.preprocess.expressions.type.Type


typealias InstanceSignature = List<LambdaName>

typealias TypeSignature = List<TypeSig>

data class InstanceName(
        val signature: InstanceSignature,
        val typeSignature: TypeSignature
) {
    override fun toString(): String {
        val name = signature.joinToString(" → ")
        return if (typeSignature.isEmpty()) name else "($name ${typeSignature.joinToString(" ")})"
    }
}

sealed class TypeSig : Replaceable<TypeSig> {

    data class Variable(
            val name: TypeVariableName
    ) : TypeSig() {
        override fun replace(map: Map<String, TypeSig>) = map[name] ?: this

        override fun toString() = name
    }

    data class Function(
            val from: TypeSig,
            val to: TypeSig
    ) : TypeSig() {
        override fun replace(map: Map<String, TypeSig>) = Function(from.replace(map), to.replace(map))

        override fun toString() = "($from → $to)"
    }

    data class Application(
            val name: InstanceName
    ) : TypeSig() {
        override fun replace(map: Map<String, TypeSig>) = Application(InstanceName(
                name.signature,
                name.typeSignature.map { it.replace(map) }
        ))

        override fun toString() = name.toString()
    }
}

fun Type.toSignature(): TypeSig = when (this) {
    is Type.Variable -> TypeSig.Variable(name)
    is Type.Function -> TypeSig.Function(from.toSignature(), to.toSignature())
    is Type.Application -> TypeSig.Application(InstanceName(signature, typeSignature))
}