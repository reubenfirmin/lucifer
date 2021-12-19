import io.Color
import io.Color.*
import io.IOHelpers.ansiColor
import io.IOHelpers.ansiFg
import model.ProcessRecord
import view.Table
import kotlin.math.min

class LSOFReporter(val userResolver: UserResolver, recs: Map<Int, ProcessRecord>) {

    val records: List<ProcessRecord> =recs.values
        .sortedByDescending { it.files.size }

    val fileSizeColors = Color.scale1.size
    val bucketSize = (records.size) / fileSizeColors

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

    /**
     * Map an item to a color
     */
    private fun rangeColor(idx: Int) = Color.scale1[min((idx / bucketSize), fileSizeColors - 1)]

    fun rawReport() {
        val rawTable = Table()
        rawTable.column("PARENT PID", width = 10)
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
        rawTable.printRow(-1)

        records.forEachIndexed { idx, record ->
            rawTable.printRow(idx,
                record.pid.toString(),
                record.parentPid.toString(),
                userResolver.user(record.user),
                record.command,
                record.files.size.toString())
        }
    }
}