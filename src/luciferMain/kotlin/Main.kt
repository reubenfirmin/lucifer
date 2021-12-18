import Col.*
import platform.posix.read

/**
 * @return if ProcessRecord is populated, consume it. Always pass the returned ParseState to the next invocation
 */
fun parseLine(line: String, parseState: ParseState? = null): Pair<ParseState, ProcessRecord?>  {
    val state = parseState ?: ParseState.new()
    val prefix = line[0]
    val col = Col.fromPrefix(prefix)
    return if (col != null) {
        // we found the next record, so output the one we're working on
        val record = if (col == PID && state.initialized) {
            val hydrated = state.record.copy(files = state.record.files.sortedBy { it.name }.toMutableList())
            if (state.record.command == "" && state.record.pid == 0) {
                println("ERROR - somehow mutated but ended up with null state")
            }
            state.record = ProcessRecord()
            hydrated
        } else {
            null
        }
        try {
            state.initialized = true
            col.mutator(state, line.substring(1))
        } catch (e: Exception) {
            println("Error parsing $col")
            throw e
        }
        state to record
    } else {
        state to null
    }
}

fun finish(state: ParseState) =
    if (state.initialized) {
        // hack
        FILE.mutator(state, "")
        state.record
    } else {
        null
    }

/**
 * XXX work around kotlin-native bug where readlines doesn't split on 0xA!
 * @return lines read from stdin, plus possibly a line fragment that didn't end with line terminator. empty list if
 * EOF
 */
fun stdinBuffer(prevFragment: String?): Triple<Array<String>, String?, Int> {
    val buffer = readLine()
    return if (buffer != null) {
        val lines = buffer.split(Char(0xA)).toTypedArray()
        if (prevFragment != null) {
            val old = lines[0]
            lines[0] = prevFragment + lines[0]
            println("Replaced [$old] with [${lines[0]}]")
        }
        if (!buffer.endsWith(Char(0xA))) {
            Triple(lines, buffer.substring(buffer.lastIndexOf(Char(0xA)) + 1), buffer.length)
        } else {
            Triple(lines, null, buffer.length)
        }
    } else if (prevFragment != null) {
        println("ERROR - end of input, but last line didn't end with NL: $prevFragment")
        Triple(arrayOf(), null, 0)
    } else {
        Triple(arrayOf(), null, 0)
    }
}

fun main() {
    // lsof can return dupe records :/
    val processMetadata: MutableMap<Int, ProcessRecord> = mutableMapOf()
    var parsed = 0
    var reads = 0
    var prevFragment: String? = null
    var kotlinBugExists = false
    var lineState: Pair<ParseState, ProcessRecord?> = ParseState.new() to null
    // read from stdin until eof
    while (true) {
        val buffer = stdinBuffer(prevFragment)
        reads ++
        prevFragment = buffer.second // pass any tail to the next buffer
        parsed += buffer.third
        kotlinBugExists = kotlinBugExists || buffer.first.size > 0
        if (buffer.first.isNotEmpty()) {
            buffer.first.forEach { line ->
                lineState = parseLine(line, lineState.first)
                if (lineState.second != null) {
                    val newRecord = lineState.second!!
                    if (!lineState.first.initialized) {
                        throw Exception("ERROR - trying to add record that wasn't initialized")
                    }
                    if (processMetadata.containsKey(newRecord.pid) && processMetadata[newRecord.pid] != newRecord) {
                        println("ERROR - duplicate process records with differing content")
//                        val old = processMetadata[newRecord.pid]!!
//                        println("${newRecord.pid} || ${old.pid}")
//                        println("${newRecord.command} || ${old.command}")
//                        println("${newRecord.user} || ${old.user}")
//                        println("${newRecord.files.size} || ${old.files.size}")
//                        for (file in newRecord.files.zip(old.files)) {
//                            if (file.first != file.second) {
//                                println("${file.first.name} ${file.first.descriptor} ${file.first.type} || ${file.second.name} ${file.second.descriptor} ${file.second.type}")
//                            }
//                        }
                    }
                    processMetadata[newRecord.pid] = newRecord
                }
            }
        } else {
            val last = finish(lineState.first)
            if (last != null) {
                processMetadata[last.pid] = last
            }
            processMetadata
                .values
                .sortedByDescending { it.files.size }
                .forEach {
                    println("${it.pid}\t${it.user}\t${it.command}\t\t\t${it.files.size}")
                }
            println()
            println("DONE; parsed $parsed; reads $reads")
            if (!kotlinBugExists) {
                println("Kotlin bug may have been fixed")
            }
            break
        }
    }
}

enum class Col(private val prefix: Char,
               val mutator: (ParseState, String) -> Unit) {
    COMMAND('c', {p, s -> p.record.command = s}),
    PID('p', {p, s -> p.record.pid = s.toInt() }),
    USER('u', {p, s -> p.record.user = s}),
    FILE('f', {p, s ->
        // we'd collected a prior record
        if (p.descriptor != null) {
            p.record.files.add(FileRecord(p.descriptor!!, p.type ?: "none", p.name ?: "none"))
            p.type = null
            p.name = null
        }
        p.descriptor = s
    }),
    TYPE('t', {p, s -> p.type = s}),
    NAME('n', {p, s -> p.name = s});

    companion object {
        private val prefixMap = values().associateBy { it.prefix }

        fun fromPrefix(prefix: Char) = prefixMap[prefix]
    }
}

data class ProcessRecord(var pid: Int = 0,
                        var command: String = "",
                        var user: String = "",
                        var files: MutableList<FileRecord> = mutableListOf())

data class FileRecord(val descriptor: String, val type: String, val name: String)

data class ParseState(var record: ProcessRecord,
                      var initialized: Boolean,
                      var descriptor: String?,
                      var type: String?,
                      var name: String?) {
    companion object {
        fun new() = ParseState(ProcessRecord(), false, null, null, null)
    }
}
