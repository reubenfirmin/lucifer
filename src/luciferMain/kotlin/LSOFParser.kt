import Field.*
import model.ParseState
import model.ProcessRecord
import kotlin.native.concurrent.freeze

/**
 * To use - call parseLine repeatedly until it returns false. Then call yield to get the parsed data.
 *
 * @param debug true to print record differences to stdout
 */
class LSOFParser(private val debug: Boolean) {

    private var parseState = ParseState.new()
    private val records: MutableMap<Int, ProcessRecord> = mutableMapOf()

    /**
     * @return false if encountered null (indicating no more lines)
     */
    fun parseLine(line: String?): Boolean  {
        return if (line == null) {
            finish()
            false
        } else {
            val prefix = if (line.isNotEmpty()) { line[0] } else { null }
            val col = prefix?.let { Field.fromPrefix(it) }
            if (col != null) {
                // we found the next record, so output the one we're working on
                if (col == PID && parseState.initialized) {
                    storeRecord(parseState.record.copy(
                        files = parseState.record.files.sortedBy { it.name }.toMutableList()))

                    parseState.clear()
                }

                try {
                    parseState.initialized = true
                    col.mutator(parseState, if (line.length > 1) { line.substring(1) } else { "" })
                } catch (e: Exception) {
                    println("""Error parsing $col ${line.length} 
                        ${if (line.length > 1) { line.substring(1) } else { "" }}""")
                    throw e
                }
            }
            true
        }
    }

    // freeze is a kotlin native thing
    fun yieldData() = records.toMap().freeze()

    private fun finish() {
        if (parseState.initialized) {
            // XXX hack
            FILE.mutator(parseState, "")
            storeRecord(parseState.record)
        }
    }

    /**
     * lsof -E can return duplicate "records" for a given process, although these records can have differing sets of files,
     * because they were sampled at different points in time (apparently?). Determine which is the better record to store,
     * and update the records map. (It's possible we could decide to merge them in future.)
     */
    private fun storeRecord(newRecord: ProcessRecord) {
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
}


/**
 * Fields we are interested in, and associated parse logic.
 */
enum class Field(private val prefix: Char,
                 val mutator: (ParseState, String) -> Unit) {
    COMMAND('c', {p, s -> p.record.command = s}),
    PID('p', {p, s -> p.record.pid = s.toInt() }),
    PPID('R', {p, s -> p.record.parentPid = s.toInt()}),
    USER('u', {p, s -> p.record.user = s.toInt()}),
    FILE('f', {p, s ->
        // we'd collected a prior record
        if (p.descriptor != null) {
            p.record.files.add(model.FileRecord(p.descriptor!!, p.type ?: "", p.name ?: "none", p.protocol ?: ""))
            p.type = null
            p.name = null
            p.protocol = null
        }
        p.descriptor = s
    }),
    TYPE('t', {p, s -> p.type = s}),
    NAME('n', {p, s -> p.name = s}),
    PROTOCOL('P', {p, s -> p.protocol = s});

    companion object {
        private val prefixMap = values().associateBy { it.prefix }

        fun fromPrefix(prefix: Char) = prefixMap[prefix]
    }
}