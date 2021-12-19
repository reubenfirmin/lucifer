import Field.*
import model.ParseState
import model.ProcessRecord

/**
 * @param debug true to print record differences to stdout
 */
class LSOFParser(private val debug: Boolean) {

    private val records: MutableMap<Int, ProcessRecord> = mutableMapOf()

    fun finish(state: ParseState) =
        if (state.initialized) {
            // hack
            FILE.mutator(state, "")
            state.record
        } else {
            null
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
     * lsof -E can return duplicate "records" for a given process, although these records can have differing sets of files,
     * because they were sampled at different points in time (apparently?). Determine which is the better record to store,
     * and update the records map. (It's possible we could decide to merge them in future.)
     */
    fun storeRecord(newRecord: ProcessRecord) {

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

    fun printReport() {
        // reporting
        records
            .values
            .sortedByDescending { it.files.size }
            .forEach {
                println("${it.pid}\t${it.user}\t${it.command}\t\t\t${it.files.size}")
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
            p.record.files.add(model.FileRecord(p.descriptor!!, p.type ?: "none", p.name ?: "none"))
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