package service

import io.IOHelpers
import model.NetworkMetadata

/**
 * Runs ss with some magic invocations to get additional data about network connections
 */
class NetworkProcessResolver(val buffer: ByteArray, val whoisResolver: WhoisResolver) {

    private val networkProcesses: MutableMap<Pair<Int, Int>, NetworkMetadata> = mutableMapOf()
    private var attempted = false

    private fun lookupProcesses(feedback: () -> Unit) {
        if (attempted) {
            return
        }
        attempted = true
        try {
            /**
             * Handles:
            [::ffff:127.0.0.1]:39358
            192.168.68.108:47512
             */
            fun String.addressParser() = if (startsWith("[")) {
                val res = split("]")
                listOf(res[0].substring(1), res[1].substring(1))
            } else {
                split(":")
            }

            IOHelpers.runCommand("ss -tuiOpH", buffer)
                .split("\n")
                .map {
                    it.split(" ")
                        .map { part -> part.trim() }
                        .filterNot { part -> part.isEmpty() }
                }.map {
                    val local = it[4].addressParser()
                    val peer = it[5].addressParser()
                    var pid = -1
                    var fd = -1
                    var bytesRc = -1L
                    var bytesSn = -1L
                    var lastSn = -1
                    var lastRc = -1
                    for (idx in 6 until it.size) {
                        val part = it[idx]
                        when {
                            part.startsWith("users") -> {
                                val users = part.split(",")
                                pid = users[1].substring(4).toInt()
                                fd = users[2].removeSuffix("))").substring(3).toInt()
                            }
                            part.startsWith("bytes_received") -> bytesRc = part.split(":")[1].toLong()
                            part.startsWith("bytes_sent") -> bytesSn = part.split(":")[1].toLong()
                            part.startsWith("lastsnd") -> lastSn = part.split(":")[1].toInt()
                            part.startsWith("lastrcv") -> lastRc = part.split(":")[1].toInt()
                        }
                    }
                    feedback.invoke()
                    NetworkMetadata(pid, fd, local[0], local[1], peer[0], peer[1], bytesRc, bytesSn, lastRc,
                        lastSn, whoisResolver.orgname(peer[0]))
                    // TODO probably simplify and just go by fd
                }.associateBy { it.pid to it.fd }
        } catch (e: Exception) {
            println("ERROR - Couldn't initialize network resolver. Maybe ss is not available on this system. Message: ${e.message}")
            mapOf()
        }.entries.forEach {
            networkProcesses[it.key] = it.value
        }
    }

    fun networkProcess(pid: Int, fd: String, feedback: () -> Unit = {}): NetworkMetadata? {
        lookupProcesses(feedback)
        // TODO type conversion each lookup is potentially expensive...
        return networkProcesses[pid to fd.toInt()]
    }
}