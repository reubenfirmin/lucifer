package service

import model.ProcessMetadata
import model.ProcessRecord

/**
 * Queries the set of processes.
 */
class ProcessesQuerier(private val data: Map<Int, ProcessRecord>,
                       private val userResolver: UserResolver,
                       private val processResolver: ProcessResolver,
                       private val networkProcessResolver: NetworkProcessResolver) {

    private val records: List<ProcessRecord> = data.values
        .sortedByDescending { it.files.size }

    private val recordsByUser = records
        .groupBy { it.user }

    private val internetProtocols = setOf("TCP", "UDP")

    /**
     * Get the top N unique commands
     */
    // TODO not currently used; maybe split command and args...? or handle metadata commands here
    //  in case of "wide"
    fun topNCommands(n: Int) = records
        .asSequence()
        .map { it.command } // raw list of commands
        .groupBy { it } // command -> List<command>
        .map { it.value } // List<List<command>>
        .sortedByDescending { it.size }
        .take(n)
        .map { it.first() }
        .withIndex()

    /**
     * Get the top N most common parents
     */
    fun topNParents(n: Int) = records
        .asSequence()
        .map { it.parentPid } // raw list of parents
        .groupBy { it } // parent -> List<parent>
        .map { it.value } // List<List<parent>>
        .sortedByDescending { it.size }
        .take(n)
        .map { it.first() }
        .withIndex()

    /**
     * Count of records by type, sorted descending
     */
    fun recordsByTypes(recs: List<ProcessRecord>) = recs
        .flatMap { it.files }
        .toSet()
        .groupBy { it.type }
        .map { it.key to it.value.size }
        .sortedByDescending { it.second }

    /**
     * User to [recordsByTypes]
     */
    fun recordsByUserByTypes() = recordsByUser
        .map { (userResolver.user(it.key)) to recordsByTypes(it.value) }

    /**
     * Records containing UDP/TCP connections to files containing those connections, sorted by number of connections
     */
    fun internetConnections(recs: List<ProcessRecord>, feedback: () -> Unit = {}) = recs
        .map { proc ->
            proc to proc.files.filter { file ->
                file.protocol in internetProtocols
            }.map { file ->
                file to networkProcessResolver.networkProcess(proc.pid, file.descriptor, feedback)
            }
        }.filter {
            it.second.isNotEmpty()
        }.sortedBy { it.second.size }

    /**
     * User to [internetConnections]
     */
    fun internetConnectionsByUser(feedback: () -> Unit = {}) = recordsByUser
        .map { (userResolver.user(it.key)) to internetConnections(it.value, feedback) }

    /**
     * Records with index, and potentially additional metadata
     */
    fun processesByFileSizeWithMetadata() = records.mapIndexed {
            idx, record -> idx to record.enhance()
    }

    /**
     * Process record if exists, with metadata
     */
    fun processWithMetadata(pid: Int) = data[pid]?.enhance()

    /**
     * Number of processes returned by lsof
     */
    fun numRecords() = records.size

    /**
     * Processes with the specified command
     */
    fun processesByCommand(command: String) = records
        .filter { it.command == command }
        .map { it.enhance() }

    private fun ProcessRecord.enhance() =
        EnhancedProcess(this, processResolver.process(this.pid), userResolver.user(this.user))

    /**
     * @param record lsof record of a process, including files
     * @param metadata retrieved from ps
     * @poram userName resolved by looking up uid
     */
    data class EnhancedProcess(val record: ProcessRecord, val metadata: ProcessMetadata?, val userName: String)
}