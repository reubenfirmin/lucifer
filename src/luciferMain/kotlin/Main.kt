import Field.*
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.*

const val MAX_READ = 4096
val buffer = ByteArray(MAX_READ)

/**
 * @return if ProcessRecord is populated, consume it. Always pass the returned ParseState to the next invocation
 */
fun parseLine(line: String, parseState: ParseState? = null): Pair<ParseState, ProcessRecord?>  {
    val state = parseState ?: ParseState.new()
    // XXX hack
    val prefix = if (line.isNotEmpty()) { line[0] } else { '0' }
    val col = Field.fromPrefix(prefix)
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
        Field.FILE.mutator(state, "")
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

/**
 * Helper function for debugging duplicate record differences.
 */
fun compare(oldRecord: ProcessRecord, newRecord: ProcessRecord) {
    println("ERROR(?) - duplicate process records with differing content ---")
    println("${newRecord.pid} || ${oldRecord.pid}")
    if (newRecord.command != oldRecord.command) {
        println("COMMAND -- ${newRecord.command} || ${oldRecord.command}")
    }
    if (newRecord.user != oldRecord.user) {
        println("USER -- ${newRecord.user} || ${oldRecord.user}")
    }
    if (newRecord.files.size != oldRecord.files.size) {
        println("FILE COUNT -- ${newRecord.files.size} || ${oldRecord.files.size}")
    }
    for (file in newRecord.files.zip(oldRecord.files)) {
        if (file.first != file.second) {
            if (file.first.name != file.second.name) {
                println("FILE NAME -- ${file.first.name} ${file.second.name}")
            }
            if (file.first.descriptor != file.second.descriptor) {
                println("FILE DESCRIPTOR -- ${file.first.descriptor} ${file.second.descriptor}")
            }
            if (file.first.type != file.second.type) {
                println("FILE TYPE -- ${file.first.type} ${file.second.type}")
            }
        }
    }
}

/**
 * Print to stderr.
 */
fun printErr(message: String) {
    fprintf(stderr!!, message + "\n")
    fflush(stderr!!)
}

/**
 * lsof -E can return duplicate "records" for a given process, although these records can have differing sets of files,
 * because they were sampled at different points in time (apparently?). Determine which is the better record to store,
 * and update the records map. (It's possible we could decide to merge them in future.)
 */
fun storeRecord(newRecord: ProcessRecord, records: MutableMap<Int, ProcessRecord>, debug: Boolean) {

    val toAdd = if (records.containsKey(newRecord.pid) && records[newRecord.pid] != newRecord) {

        val existing = records[newRecord.pid]!!
        if (debug) {
            compare(existing, newRecord)
        }
        if (existing.files.size > newRecord.files.size) {
            existing
        } else {
            newRecord
        }
    } else {
        newRecord
    }

    records[newRecord.pid] = toAdd
}

fun main(args: Array<String>) {
    println("Lucifer - parsing input...")

    // TODO brutal hack for now. there is an arg parser available in kotlinx, but it adds 1MB to the compiled binary
    val debug = args.contains("--debug")
    val err = args.contains("--err")

    val processMetadata: MutableMap<Int, ProcessRecord> = mutableMapOf()
    var lineState: Pair<ParseState, ProcessRecord?> = ParseState.new() to null

    // General algorithm:
    //
    // Read a line at a time until EOF.
    //
    // Every time we hit a line starting with "p" (pid / process id), then store the record we've been accumulating in
    // processState, and start storing a new record.
    //
    // Records are variable length, because there is a 1:N relationship between processes and files (i.e. files are
    // listed within the overall process record.)
    while (true) {
        val line = readLine()
        if (line != null) {
            if (err) {
                printErr(line)
            }
            lineState = parseLine(line, lineState.first)

            if (lineState.second != null) {
                if (!lineState.first.initialized) {
                    throw Exception("ERROR - trying to add record that wasn't initialized")
                }
                storeRecord(lineState.second!!, processMetadata, debug)
            }
        } else {
            val last = finish(lineState.first)
            if (last != null) {
                storeRecord(last, processMetadata, debug)
            }

            // reporting
            processMetadata
                .values
                .sortedByDescending { it.files.size }
                .forEach {
                    println("${it.pid}\t${it.user}\t${it.command}\t\t\t${it.files.size}")
                }
            println()
            println("DONE")
            break
        }
    }
}

/**
 * Fields we are interested in, and associated parse logic.
 */
enum class Field(private val prefix: Char,
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

/**
 * A record describing a process' state.
 */
data class ProcessRecord(var pid: Int = 0,
                        var command: String = "",
                        var user: String = "",
                        var files: MutableList<FileRecord> = mutableListOf())

/**
 * A record describing a file associated with a process.
 */
data class FileRecord(val descriptor: String, val type: String, val name: String)

/**
 * A mutable state object that can be passed around when iterating through the output.
 */
data class ParseState(var record: ProcessRecord,
                      var initialized: Boolean,
                      var descriptor: String?,
                      var type: String?,
                      var name: String?) {

    companion object {
        fun new() = ParseState(ProcessRecord(), false, null, null, null)
    }
}