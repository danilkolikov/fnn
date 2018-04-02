package thesis.eval

import thesis.preprocess.spec.DataPattern
import thesis.preprocess.spec.Spec
import thesis.preprocess.spec.TypeSpec

/**
 * Callable lambda which arguments are plain array and list of functions
 *
 * @author Danil Kolikov
 */
sealed class EvalSpec {

    abstract fun eval(arguments: DataBag): EvalResult

    sealed class Variable : EvalSpec() {
        class Object(
                val spec: Spec.Variable.Object
        ) : Variable() {

            override fun eval(arguments: DataBag) = EvalResult.Data(
                    arguments.data.subList(spec.positions.first, spec.positions.second)
            )

            override fun toString() = spec.toString()
        }

        class Function(
                val spec: Spec.Variable.Function
        ) : Variable() {

            override fun eval(arguments: DataBag) = EvalResult.Function(
                    arguments.functions[spec.position]
            )

            override fun toString() = spec.toString()
        }

        class External(
                val spec: Spec.Variable.External,
                val function: EvalSpec.Function.Guarded
        ) : Variable() {

            override fun eval(arguments: DataBag) = EvalResult.Function(
                    function
            )

            override fun toString() = spec.name
        }
    }

    class Object(private val spec: Spec.Object) : EvalSpec() {

        override fun eval(arguments: DataBag) = EvalResult.Data(spec.data)

        override fun toString() = "[${spec.type}: ${spec.data}]"
    }

    sealed class Function : EvalSpec() {

        abstract val spec: Spec.Function

        class Constructor(
                override val spec: Spec.Function.Constructor
        ) : Function() {

            override fun eval(arguments: DataBag): EvalResult {
                val offset = spec.memoryRepresentation.info.offset
                val data = List(spec.memoryRepresentation.typeSize, {
                    if (it < offset) {
                        return@List 0.toShort()
                    }
                    if (it > offset + spec.memoryRepresentation.typeSize) {
                        return@List 0.toShort()
                    }
                    return@List arguments.data[it - offset]
                })

                return EvalResult.Data(data)
            }

            override fun toString() = spec.name
        }

        class Guarded(
                override val spec: Spec.Function.Guarded,
                val cases: List<Case>
        ) : Function() {

            override fun eval(arguments: DataBag): EvalResult {
                for (case in cases) {
                    val presence = case.getPresence(arguments)
                    if (presence == 0.toShort()) {
                        continue
                    }
                    return case.body.eval(arguments)
                }
                throw IllegalStateException("No available alternatives in ${spec.name}")
            }

            override fun toString() = "(${spec.name} = ${cases.joinToString(", ")})"

            class Case(
                    val spec: Spec.Function.Guarded.Case,
                    val body: EvalSpec
            ) {
                fun getPresence(
                        arguments: DataBag
                ) = (if (spec.patterns.all {
                            when (it) {
                                is DataPattern.Object -> arguments.data[it.position]
                                is DataPattern.Variable -> {
                                    val data = arguments.data.subList(it.start, it.end)
                                    val info = it.type.toDataTypeInfo(data)
                                    info.presence
                                }
                            } == 1.toShort()
                        }) 1 else 0).toShort()

                override fun toString() = body.toString()

                private fun TypeSpec.toDataTypeInfo(data: List<Short>): DataTypeInformation = when (this) {
                    is TypeSpec.Literal -> DataTypeInformation.Literal(this, data[position])
                    is TypeSpec.Product -> {
                        val operands = this.operands.map { it.toDataTypeInfo(data) }
                        val presence = operands.foldRight(1.toShort(), { a, b -> (a.presence * b).toShort() })
                        DataTypeInformation.Product(this, operands, presence)
                    }
                    is TypeSpec.Sum -> {
                        val operands = this.operands.map { it.toDataTypeInfo(data) }
                        val presence = operands.map { it.presence }.sum().toShort()
                        DataTypeInformation.Sum(this, operands, presence)
                    }
                }

            }
        }

        class Anonymous(
                override val spec: Spec.Function.Anonymous,
                val body: EvalSpec
        ) : Function() {
            override fun eval(arguments: DataBag) = body.eval(arguments)
        }
    }

    class Application(
            val spec: Spec.Application,
            val operands: List<EvalSpec>
    ) : EvalSpec() {
        override fun eval(arguments: DataBag): EvalResult {
            // Resolve variables and eval applications
            val resolved = operands.mapIndexed { index, rawLambda ->
                when {
                    spec.call.contains(index) ->
                        // Save result of evaluation of application or resolution of variable
                        rawLambda.eval(arguments)
                    spec.constants.contains(index) ->
                        // Save result of constant evaluation
                        rawLambda.eval(DataBag.EMPTY)
                    else ->
                        // Not variable, not application, not constant => it's a function
                        EvalResult.Function(rawLambda as Function)
                }
            }

            val func = (resolved.first() as? EvalResult.Function ?: throw IllegalArgumentException(
                    "First operand of application should be a function, got ${resolved.first()}"
            )).spec

            val args = resolved.drop(1)
            val data = spec.data.flatMap { (args[it] as EvalResult.Data).data }
            val functions = spec.functions.map { (args[it] as EvalResult.Function).spec }
            val newArgs = DataBag(data, functions)
            val raw = arguments.getArgumentsBefore(func.spec.closurePointer).append(newArgs)
            return func.eval(raw)
        }

        override fun toString() = "(${operands.joinToString(" ")})"
    }
}