/**
 * Some extension functions
 *
 * @author Danil Kolikov
 */
package thesis.utils

fun List<Short>.mult(): Int {
    var res = 1
    for (sh in this) {
        res *= sh
    }
    return res
}