package thesis.preprocess.results

/**
 * Map-like structure for storage of instances
 *
 * @author Danil Kolikov
 */
class Instances<T> {
    val map = LinkedHashMap<InstanceSignature, MutableMap<TypeSignature, T>>()

    fun set(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        map.computeIfAbsent(instanceSignature, { mutableMapOf() })
        map[instanceSignature]!![typeSignature] = obj
    }

    fun get(instanceSignature: InstanceSignature, typeSignature: TypeSignature) = map[instanceSignature]?.get(typeSignature)

    fun putIfAbsent(instanceSignature: InstanceSignature, typeSignature: TypeSignature, obj: T) {
        map.computeIfAbsent(instanceSignature, { mutableMapOf() })
        map[instanceSignature]!!.putIfAbsent(typeSignature, obj)
    }

    fun getInstances(instanceSignature: InstanceSignature) = map[instanceSignature] ?: emptyMap<TypeSignature, T>()

    fun forEach(action: (InstanceSignature, TypeSignature, T) -> Any) {
        map.forEach { sig, types -> types.forEach { type, value -> action(sig, type, value) } }
    }

    override fun toString() = map.toString()
}