import io.Color
import io.IOHelpers.ansiFg
import model.ProcessRecord
import view.Table
import kotlin.math.max
import kotlin.math.min

class LSOFReporter(val records: Map<Int, ProcessRecord>) {

    val fileSizeColors = 6
    val bucketSize = (records.size) / fileSizeColors

    /**
     * Map an item to a color
     */
    private fun rangeColor(idx: Int) = Color.scale1[min((idx / bucketSize), fileSizeColors - 1)]

    fun rawReport() {
        val rawTable = Table()
        rawTable.column("PID", width = 10)
                .column("USER", width = 20)
                .column("COMMAND", width = 40)
                .column("FILES", width = 20)

        rawTable.printHeading()
        rawTable.printRow()

        records
            .values
            .sortedByDescending { it.files.size }
            .forEachIndexed { idx, record ->
                rawTable.printRow(record.pid.toString(), record.user, record.command,
                    ansiFg(rangeColor(idx), record.files.size.toString()))
            }
    }
}