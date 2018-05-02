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

    private val map = mutableMapOf<InstanceSignature, LinkedHashMap<TypeSignature, LinkedValue<T>>>()

    private val start = LinkedValue<T>(null, null)
    private var last = start

    operator fun set(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        map.computeIfAbsent(instanceSignature, { LinkedHashMap() })
        map[instanceSignature]?.let {
            if (!it.containsKey(typeSignature)) {
                // Add new value
                val new = LinkedValue(Value(obj, instanceSignature, typeSignature), null)
                last.next = new
                last = new
                it.put(typeSignature, new)
            } else {
                it[typeSignature]?.let {
                    it.value = Value(obj, instanceSignature, typeSignature)
                }
            }
        }
    }

    operator fun get(instanceSignature: InstanceSignature, typeSignature: TypeSignature) =
            map[instanceSignature]?.get(typeSignature)?.value?.data

    operator fun get(instanceSignature: InstanceSignature, type: Parametrised<Type>) =
            this[instanceSignature, type.typeSignature()]

    operator fun set(instanceName: InstanceName, obj: T) {
        this[instanceName.signature, instanceName.typeSignature] = obj
    }

    operator fun get(instanceName: InstanceName) = this[instanceName.signature, instanceName.typeSignature]

    fun putIfAbsent(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        if (containsKey(instanceSignature, typeSignature)) {
            return
        }
        this[instanceSignature, typeSignature] = obj
    }

    fun putIfAbsent(instanceName: InstanceName, obj: T) {
        this.putIfAbsent(instanceName.signature, instanceName.typeSignature, obj)
    }

    fun getInstances(instanceSignature: InstanceSignature)
            = map[instanceSignature]?.mapValuesTo(LinkedHashMap(), { it.value.value!!.data }) ?: emptyMap<TypeSignature, T>()

    fun forEach(action: (InstanceSignature, TypeSignature, T) -> Unit) {
        var cur : LinkedValue<T>? = start
        while (cur != null) {
            cur.value?.let { action(it.instanceSignature, it.typeSignature, it.data) }
            cur = cur.next
        }
    }

    fun containsKey(instanceSignature: InstanceSignature, typeSignature: TypeSignature) =
            map.containsKey(instanceSignature) && map[instanceSignature]!!.containsKey(typeSignature)

    fun isEmpty() = map.isEmpty() || map.all { it.value.isEmpty() }

    override fun toString() = map.toString()

    private data class Value<T>(
            val data: T,
            val instanceSignature: InstanceSignature,
            val typeSignature: TypeSignature
    )

    private data class LinkedValue<T>(
            var value: Value<T>?,
            var next: LinkedValue<T>?
    )
}