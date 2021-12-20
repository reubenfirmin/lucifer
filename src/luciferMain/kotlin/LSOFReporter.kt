import io.Color
import io.Color.*
import io.IOHelpers.ansiColor
import io.IOHelpers.ansiFg
import io.IOHelpers.ansiUnderline
import io.IOHelpers.runCommand
import model.ProcessRecord
import view.Table
import kotlin.math.min

/**
 * Generate various reports from the data.
 */
class LSOFReporter(private val userResolver: UserResolver,
                   private val processResolver: ProcessResolver,
                   private val recs: Map<Int, ProcessRecord>,
                   private val formatting: Boolean,
                   buffer: ByteArray) {

    private val records: List<ProcessRecord> =recs.values
        .sortedByDescending { it.files.size }

    private val recordsByUser = records
        .groupBy { it.user }

    private fun reportHeading(heading: String) {
        println(if (formatting) {
            ansiUnderline(heading)
        } else {
            heading
        })
    }

    private val terminalWidth: Int

    init {
        val width = runCommand("tput cols", buffer)
        terminalWidth = width.toInt()
    }

    fun byProcessReport() {
        reportHeading("OPEN FILES BY PROCESS")

        val fileSizeColors = Color.scale1.size
        val bucketSize = (records.size) / fileSizeColors

        /**
         * Map an item to a color
         */
        fun rangeColor(idx: Int) = Color.scale1[min((idx / bucketSize), fileSizeColors - 1)]

        val numberMostCommonCommands = Color.highlights.size

        // TODO maybe split command and args...? or handle metadata commands here in case of "wide"
        // TODO get max command length
        val commands = records
            .asSequence()
            .map { it.command }
            .groupBy { it }
            .map { it.value }
            .sortedByDescending { it.size }
            .take(numberMostCommonCommands)
            .map { it.first() }
            .withIndex()
            .associate { it.value to Color.highlights[it.index] }

        val wide = terminalWidth > 100

        val rawTable = Table(formatting, terminalWidth)
                .column("PARENT PID", width = 10)
                .column("PID", width = 10)
                .column("USER", width = 20)
                .column("FILES", width = 8) { idx, _, size ->
                    ansiFg(rangeColor(idx), size)
                }
                .optionalColumn(wide, "CPU", width = 5) // TODO generalize color ranges on these
                .optionalColumn(wide, "MEM", width = 5)
                .optionalColumn(wide, "TIME", width = 8)
                .column("COMMAND", consumeRemainingWidth = true) { _, rawCommand, paddedCommand ->
                    val commandCol = commands[rawCommand]

                    if (commandCol != null) {
                        ansiColor(BLACK, commandCol, paddedCommand)
                    } else {
                        paddedCommand
                    }
                }


        rawTable.printHeading()

        records.forEachIndexed { idx, record ->
            val metadata = processResolver.process(record.pid)

            rawTable.printRow(idx,
                record.parentPid.toString(),
                record.pid.toString(),
                userResolver.user(record.user),
                record.files.size.toString(),
                metadata?.cpu?.toString() ?: "",
                metadata?.memory?.toString() ?: "",
                metadata?.cpuTime?.toString() ?: "",
                if (wide && metadata != null) { metadata.command } else { record.command })
        }
    }

    fun fileTypeUserReport() {
        reportHeading("OPEN FILES BY TYPE BY USER")

        fun byTypes(recs: List<ProcessRecord>) = recs
            .flatMap { it.files }
            .toSet()
            .groupBy { it.type }
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }

        val byUser = recordsByUser
            .map { it.key to byTypes(it.value) }

        val userTypeTable = Table(formatting, terminalWidth)
            .column("USER")
            .column("TYPE")
            .column("COUNT")

        userTypeTable.printHeading()

        var index = 0
        byUser.forEach {  userSet ->
            userSet.second.forEach { typesSet ->
                userTypeTable.printRow(index++,
                    userResolver.user(userSet.first),
                    typesSet.first,
                    typesSet.second.toString())
            }
        }
    }

    fun networkConnectionsReport() {
        reportHeading("INTERNET CONNECTIONS BY USER")

        val networkProtocols = setOf("TCP", "UDP")

        fun networkConnections(recs: List<ProcessRecord>) = recs
            .map { it to it.files.filter { file ->
                file.protocol in networkProtocols
            }}.filter { it.second.isNotEmpty() }
            .sortedBy { it.second.size }

        val byUser = recordsByUser
            .map { it.key to networkConnections(it.value) }

        val narrow = terminalWidth < 110

        val networkTable = Table(formatting, terminalWidth)
            .column("USER", width = if (narrow) { 10 } else { 15 })
            .column("COMMAND", width = if (narrow) { 7 } else { 15 })
            .optionalColumn(!narrow, "PARENT PID", width = 10)
            .column("PID", width = 10)
            .optionalColumn(!narrow, "TYPE", width = 4)
            .optionalColumn(!narrow, "PROTO", width = 6)
            .column("CONNECTION", consumeRemainingWidth = true)

        networkTable.printHeading()

        var index = 0
        byUser.forEach { userSet ->
            userSet.second.forEach { processSet ->
                processSet.second.forEach { fileSet ->
                    networkTable.printRow(index++,
                        userResolver.user(userSet.first),
                        processSet.first.command,
                        processSet.first.parentPid.toString(),
                        processSet.first.pid.toString(),
                        fileSet.type,
                        fileSet.protocol,
                        fileSet.name
                    )
                }
            }
        }
    }

    fun processReport(pid: Int) {
        val processTable = Table(formatting, terminalWidth)
            .column("PARENT PID", width = 10)
            .column("PID", width = 10)
            .column("USER", width = 20)
            .column("COMMAND", consumeRemainingWidth = true)

        processTable.printHeading()

        // TODO additional detail metadata for this process
        val processRecord = recs[pid]
        if (processRecord == null) {
            println("Process $pid not found")
        } else {
            val metadata = processResolver.process(pid)
            processTable.printRow(0,
                processRecord.parentPid.toString(),
                processRecord.pid.toString(),
                userResolver.user(processRecord.user),
                metadata?.command ?: processRecord.command
            )

            println()
            val fileTable = Table(formatting, terminalWidth)
                .column("DESCRIPTOR", width = 10)
                .column("TYPE", width = 10)
                .column("PROTO", width = 6)
                .column("NAME", consumeRemainingWidth = true)

            fileTable.printHeading()

            processRecord.files
                .sortedBy { it.name }
                .forEachIndexed { idx, file ->
                    fileTable.printRow(idx,
                        file.descriptor,
                        file.type,
                        file.protocol,
                        file.name
                    )
                }
        }
    }
}