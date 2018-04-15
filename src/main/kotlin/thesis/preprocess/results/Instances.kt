package thesis.preprocess.results

import thesis.preprocess.expressions.type.Parametrised
import thesis.preprocess.expressions.type.Type
import thesis.preprocess.expressions.type.typeSignature

/**
 * Map-like structure for storage of instances
 *
 * @author Danil Kolikov
 */
class Instances<T> {
    val map = LinkedHashMap<InstanceSignature, LinkedHashMap<TypeSignature, T>>()

    operator fun set(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        map.computeIfAbsent(instanceSignature, { LinkedHashMap() })
        map[instanceSignature]!![typeSignature] = obj
    }

    operator fun get(instanceSignature: InstanceSignature, typeSignature: TypeSignature) = map[instanceSignature]?.get(typeSignature)

    operator fun get(instanceSignature: InstanceSignature, type: Parametrised<Type>) =
            this[instanceSignature, type.typeSignature()]

    fun putIfAbsent(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        map.computeIfAbsent(instanceSignature, { LinkedHashMap() })
        map[instanceSignature]!!.putIfAbsent(typeSignature, obj)
    }

    fun getInstances(instanceSignature: InstanceSignature) = map[instanceSignature] ?: emptyMap<TypeSignature, T>()

    fun forEach(action: (InstanceSignature, TypeSignature, T) -> Unit) {
        map.forEach { sig, types -> types.forEach { type, value -> action(sig, type, value) } }
    }

    fun containsKey(instanceSignature: InstanceSignature, typeSignature: TypeSignature) =
            map.containsKey(instanceSignature) && map[instanceSignature]!!.containsKey(typeSignature)

    override fun toString() = map.toString()
}