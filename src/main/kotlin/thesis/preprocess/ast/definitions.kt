package thesis.preprocess.ast


typealias TypeName = String

typealias LambdaName = String

/**
 * AST for Lambda Program
 */

data class LambdaProgram(val expressions: List<LambdaProgramExpression>)

interface WithNames {
    fun getNames(): Set<TypeName>
}

sealed class LambdaProgramExpression : WithNames {
    abstract val name: String
}

data class LambdaDefinition(
        override val name: LambdaName,
        val expression: LambdaExpression
) : LambdaProgramExpression() {
    override fun getNames() = setOf(name) + expression.getNames()
}

data class TypeDefinition(
        override val name: TypeName,
        val expression: TypeExpression
) : LambdaProgramExpression() {
    override fun getNames() = setOf(name) + expression.getNames()
}

sealed class TypeExpression : WithNames

data class TypeLiteral(val name: TypeName) : TypeExpression() {
    override fun getNames() = setOf(name)
}

data class TypeSum(
        val operands: List<TypeExpression>
) : TypeExpression() {
    override fun getNames() = operands.flatMap { it.getNames() }.toSet()
}

data class TypeProduct(
        val name: String,
        val operands: List<TypeExpression>
) : TypeExpression() {
    override fun getNames() = setOf(name) + operands.flatMap { it.getNames() }.toSet()
}

sealed class LambdaExpression : WithNames

data class LambdaLiteral(val name: LambdaName) : LambdaExpression() {
    override fun getNames() = setOf(name)
}

data class LambdaAbstraction(
        val arguments: List<LambdaName>,
        val expression: LambdaExpression
) : LambdaExpression() {
    override fun getNames() = arguments.toSet() + expression.getNames()
}

data class LambdaApplication(
        val function: LambdaExpression,
        val arguments: List<LambdaExpression>
) : LambdaExpression() {
    override fun getNames() = (listOf(function) + arguments).flatMap { it.getNames() }.toSet()
}

