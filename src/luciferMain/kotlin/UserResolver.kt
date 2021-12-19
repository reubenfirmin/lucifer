import io.IOHelpers.runCommand
import model.UNKNOWN_USER

/**
 * Converts user id to username
 */
class UserResolver(val buffer: ByteArray) {

    private val users: MutableMap<Int, String> = mutableMapOf()

    fun user(id: Int) =
        users.getOrPut(id) {
            if (id == UNKNOWN_USER) {
                "UNKNOWN"
            } else {
                parseUser(runCommand("id $id", buffer))
            }
        }

    private fun parseUser(res: String): String {
        val firstBracket = res.indexOf('(')
        val secondBracket = res.indexOf(')', firstBracket)
        return res.substring(firstBracket + 1, secondBracket)
    }
}