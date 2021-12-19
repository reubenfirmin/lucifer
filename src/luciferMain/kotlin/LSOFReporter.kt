import io.Color
import io.Color.*
import io.IOHelpers.ansiColor
import io.IOHelpers.ansiFg
import io.IOHelpers.ansiUnderline
import model.ProcessRecord
import view.Table
import kotlin.math.min

/**
 * Various reports from the data.
 */
class LSOFReporter(val userResolver: UserResolver, recs: Map<Int, ProcessRecord>) {

    private val records: List<ProcessRecord> =recs.values
        .sortedByDescending { it.files.size }

    private val recordsByUser = records
        .groupBy { it.user }

    fun byProcessReport() {
        println(ansiUnderline("OPEN FILES BY PROCESS"))

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

        val rawTable = Table()
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
                record.pid.toString(),
                record.parentPid.toString(),
                userResolver.user(record.user),
                record.command,
                record.files.size.toString())
        }
    }

    fun fileTypeUserReport() {
        println(ansiUnderline("OPEN FILES BY TYPE BY USER"))

        fun byTypes(recs: List<ProcessRecord>) = recs
            .flatMap { it.files }
            .toSet()
            .groupBy { it.type }
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }

        val byUser = recordsByUser
            .map { it.key to byTypes(it.value) }

        val userTypeTable = Table()
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
        println(ansiUnderline("NETWORK CONNECTIONS BY USER"))

        val networkProtocols = setOf("TCP", "UDP")

        fun networkConnections(recs: List<ProcessRecord>) = recs
            .map { it to it.files.filter { file ->
                file.protocol in networkProtocols
            }}.filter { it.second.isNotEmpty() }
            .sortedBy { it.second.size }

        val byUser = recordsByUser
            .map { it.key to networkConnections(it.value) }

        val networkTable = Table()
            .column("USER", width = 15)
            .column("COMMAND", width = 7)
            .column("PID", width = 10)
            .column("PARENT PID", width = 10)
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
                        processSet.first.pid.toString(),
                        processSet.first.parentPid.toString(),
                        fileSet.type,
                        fileSet.protocol,
                        fileSet.name
                    )
                }
            }
        }
    }
}