import Col.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cli.*
import platform.posix.*
import platform.posix.FILE

/**
 * @return if ProcessRecord is populated, consume it. Always pass the returned ParseState to the next invocation
 */
fun parseLine(line: String, parseState: ParseState? = null): Pair<ParseState, ProcessRecord?>  {
    val state = parseState ?: ParseState.new()
    // XXX hack
    val prefix = if (line.length > 0) { line[0] } else { '0' }
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
            col.mutator(state, if (line.length > 1) { line.substring(1) } else { "" })
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
        Col.FILE.mutator(state, "")
        state.record
    } else {
        null
    }

/**
 * XXX work around kotlin-native bug where readlines doesn't split on 0xA!
 * @return lines read from stdin, plus possibly a line fragment that didn't end with line terminator (in this case
 * skip processing the last line of the array). empty array if EOF
 */
fun stdinBuffer(lsof: CPointer<FILE>, prevFragment: String?): Triple<List<String>, String?, Int> {
    val buffer = ByteArray(4096)
    val str = fgets(buffer.refTo(0), buffer.size, lsof)?.toKString() ?: ""

    return if (buffer.isNotEmpty()) {
        val lines = str.split(Char(0xA)).toMutableList()
        if (prevFragment != null) {
            val newLine = prevFragment + lines[0]
            // edge case. kotlin readline dropped a newline char where it should actually have existed, so we need
            // to add it back. we can detect this with this known pattern. there may be others
            if (wontexist.matches(newLine)) {
                lines.add(0, prevFragment)
            // otherwise, we can safely prepend the fragment to the first of the lines we're dealing with
            } else {
                lines[0] = newLine
            }
        }
        // the buffer didn't end with newline (expected most of the time) so handle the partial content specially
        // so that we can process it next time around
        if (!str.endsWith(Char(0xA))) {
            Triple(lines, str.substring(str.lastIndexOf(Char(0xA)) + 1), str.length)
        } else {
            Triple(lines, null, str.length)
        }
    } else if (prevFragment != null) {
        println("ERROR - end of input, but last line didn't end with NL: $prevFragment")
        Triple(listOf(), null, 0)
    } else {
        Triple(listOf(), null, 0)
    }
}

fun compare(oldRecord: ProcessRecord, newRecord: ProcessRecord) {
    println("ERROR - duplicate process records with differing content ---")
    println("${newRecord.pid} || ${oldRecord.pid}")
    if (newRecord.command != oldRecord.command) {
        println("${newRecord.command} || ${oldRecord.command}")
    }
    if (newRecord.user != oldRecord.user) {
        println("${newRecord.user} || ${oldRecord.user}")
    }
    if (newRecord.files.size != oldRecord.files.size) {
        println("${newRecord.files.size} || ${oldRecord.files.size}")
    }
    for (file in newRecord.files.zip(oldRecord.files)) {
        if (file.first != file.second) {
            if (file.first.name != file.second.name) {
                println("${file.first.name} ${file.second.name}")
                println("${file.first.name.length} ${file.second.name.length}")
            }
            if (file.first.descriptor != file.second.descriptor) {
                println("${file.first.descriptor} ${file.second.descriptor}")
            }
            if (file.first.type != file.second.type) {
                println("${file.first.type} ${file.second.type}")
            }
        }
    }
}

fun printErr(message: String) {
    fprintf(stderr!!, message + "\n")
    fflush(stderr!!)

}

fun main(args: Array<String>) {
    println("Lucifer - parsing input...")

    // TODO - this added over 1MB to the output size(!)
    val parser = ArgParser("Lucifer")
    val err by parser.option(ArgType.Boolean, shortName = "e",
        description = "Output parsed lines to stderr").default(false)
    val debug by parser.option(ArgType.Boolean, shortName = "d",
        description = "Output differences between files to stdout").default(false)
    parser.parse(args)

    // lsof can return dupe records :/
    val processMetadata: MutableMap<Int, ProcessRecord> = mutableMapOf()
    var parsed = 0
    var reads = 0
    var prevFragment: String? = null
    var kotlinBugExists = false
    var lineState: Pair<ParseState, ProcessRecord?> = ParseState.new() to null

    val lsof: CPointer<FILE> = popen("lsof -E", "r")!!

    // read from stdin until eof
    while (true) {
        val buffer = stdinBuffer(lsof, prevFragment)
        reads ++
        prevFragment = buffer.second // pass any tail to the next buffer
        parsed += buffer.third
        kotlinBugExists = kotlinBugExists || buffer.first.size > 0

        if (buffer.first.isNotEmpty()) {
            val lastIdx = buffer.first.size - 1
            buffer.first.forEachIndexed { idx, line ->
                // if we have a fragment, ignore the last line (since we'll process that next time)
                if (prevFragment == null || idx < lastIdx) {
                    if (err) {
                        printErr(line)
                    }
                    lineState = parseLine(line, lineState.first)
                    if (lineState.second != null) {
                        val newRecord = lineState.second!!
                        if (!lineState.first.initialized) {
                            throw Exception("ERROR - trying to add record that wasn't initialized")
                        }
                        if (debug && processMetadata.containsKey(newRecord.pid) && processMetadata[newRecord.pid] != newRecord) {
                            compare(processMetadata[newRecord.pid]!!, newRecord)
                        }
                        processMetadata[newRecord.pid] = newRecord
                    }
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
        pclose(lsof)
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

// this will never match
val wontexist = "^[a-zA-Z] [a-zA-Z].*$".toRegex()
