import Col.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cli.*
import platform.posix.*
import platform.posix.FILE

const val MAX_READ = 4096
val buffer = ByteArray(MAX_READ)

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
 * Replace kotlin's impl of readline
 * @return null if we hit EOF
 */
fun readLine(): String? {
    val read = fgets(buffer.refTo(0), MAX_READ, stdin)?.toKString() ?: return null
    return read.substring(0, read.length - 1)
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
    var reads = 0
    var lineState: Pair<ParseState, ProcessRecord?> = ParseState.new() to null

    // read from stdin until eof
    while (true) {
        val line = readLine()
        reads ++

        if (line != null) {
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
            println("DONE; reads $reads")
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

// this will never match
val wontexist = "^[a-zA-Z] [a-zA-Z].*$".toRegex()
