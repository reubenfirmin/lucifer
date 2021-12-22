package service

import io.IOHelpers

/**
 * Converts IP to org name
 */
class WhoisResolver(val buffer: ByteArray) {

    private val orgs: MutableMap<String, String?> = mutableMapOf()

    fun orgname(ip: String) =
        orgs.getOrPut(ip) {
            if (ip.startsWith("::")) {
                // XXX this is a hack. handle upstack
                ""
            } else {
                parseOrg(IOHelpers.runCommand("whois $ip", buffer))
            }
        }

    private fun parseOrg(res: String): String? {
        val org = res.split("\n")
            .firstOrNull { it.startsWith("OrgName") || it.startsWith("org-name") }
        return if (org == null) {
            println("ERROR - org name not found in whois response")
            println(res)
            null
        } else {
            return org.split(":")[1].trim()
        }
    }
}