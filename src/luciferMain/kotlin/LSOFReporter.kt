import io.Color
import io.Color.*
import io.IOHelpers.ansiColor
import io.IOHelpers.ansiFg
import io.IOHelpers.ansiUnderline
import model.ProcessRecord
import view.Table
import kotlin.math.min

/**
 * Generate various reports from the data.
 */
class LSOFReporter(private val userResolver: UserResolver,
                   private val recs: Map<Int, ProcessRecord>,
                   private val formatting: Boolean) {

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

    fun byProcessReport() {
        reportHeading("OPEN FILES BY PROCESS")

        val fileSizeColors = Color.scale1.size
        val bucketSize = (records.size) / fileSizeColors

        /**
         * Map an item to a color
         */
        fun rangeColor(idx: Int) = Color.scale1[min((idx / bucketSize), fileSizeColors - 1)]

        val numberMostCommonCommands = Color.highlights.size
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

        val rawTable = Table(formatting)
                .column("PARENT PID", width = 10)
                .column("PID", width = 10)
                .column("USER", width = 20)
                .column("COMMAND", width = 40) { _, rawCommand, paddedCommand ->
                    val commandCol = commands[rawCommand]

                    if (commandCol != null) {
                        ansiColor(BLACK, commandCol, paddedCommand)
                    } else {
                        paddedCommand
                    }
                }
                .column("FILES", width = 20) { idx, _, size ->
                    ansiFg(rangeColor(idx), size)
                }

        rawTable.printHeading()

        records.forEachIndexed { idx, record ->
            rawTable.printRow(idx,
                record.parentPid.toString(),
                record.pid.toString(),
                userResolver.user(record.user),
                record.command,
                record.files.size.toString())
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

        val userTypeTable = Table(formatting)
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

        val networkTable = Table(formatting)
            .column("USER", width = 15)
            .column("COMMAND", width = 7)
            .column("PARENT PID", width = 10)
            .column("PID", width = 10)
            .column("TYPE", width = 4)
            .column("PROTO", width = 6)
            .column("CONNECTION", width = 60)

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
        val processTable = Table(formatting)
            .column("PARENT PID", width = 10)
            .column("PID", width = 10)
            .column("USER", width = 20)
            .column("COMMAND", width = 40)

        processTable.printHeading()

        val processRecord = recs[pid]
        if (processRecord == null) {
            println("Process $pid not found")
        } else {
            processTable.printRow(0,
                processRecord.parentPid.toString(),
                processRecord.pid.toString(),
                userResolver.user(processRecord.user),
                processRecord.command
            )

            println()
            val fileTable = Table(formatting)
                .column("DESCRIPTOR", width = 10)
                .column("TYPE", width = 10)
                .column("PROTO", width = 6)
                .column("NAME", width = 80)

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