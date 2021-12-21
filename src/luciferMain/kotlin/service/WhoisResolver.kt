package service

import io.IOHelpers
import model.UNKNOWN_USER

/**
 * Converts IP to org name
 */
class WhoisResolver(val buffer: ByteArray) {

    private val orgs: MutableMap<String, String> = mutableMapOf()

    fun orgname(ip: String) =
        orgs.getOrPut(ip) {
            parseOrg(IOHelpers.runCommand("whois $ip", buffer))
        }

    private fun parseOrg(res: String): String {
        return "TODO"
    }
}