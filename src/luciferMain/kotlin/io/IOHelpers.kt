package io

import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.*

object IOHelpers {

    /**
     * Print to stderr.
     */
    fun printErr(message: String) {
        fprintf(stderr!!, message + "\n")
        fflush(stderr!!)
    }

    /**
     * Replace kotlin's impl of readLine, which is broken on native (https://youtrack.jetbrains.com/issue/KT-39495)
     * TODO consider using readln() instead (added in 1.6) (test)
     * NOTE - not threadsafe
     * @return null if we hit EOF
     */
    fun readLine(): String? {
        val read = fgets(buffer.refTo(0), MAX_READ, stdin)?.toKString() ?: return null
        return read.substring(0, read.length - 1)
    }


    const val MAX_READ = 4096
    val buffer = ByteArray(MAX_READ)
}