import io.IOHelpers.runCommand

/**
 * Converts user id to username
 */
class UserResolver(val buffer: ByteArray) {

    private val users: MutableMap<Int, String> = mutableMapOf()

    fun user(id: Int) =
        users.getOrPut(id) {
            val user = runCommand("id $id", buffer)
            parseUser(user)
        }

    private fun parseUser(res: String): String {
        val firstBracket = res.indexOf('(')
        val secondBracket = res.indexOf(')', firstBracket)
        return res.substring(firstBracket + 1, secondBracket)
    }
}