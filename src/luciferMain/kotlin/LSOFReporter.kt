import model.ProcessRecord
import view.Table

class LSOFReporter(val records: Map<Int, ProcessRecord>) {

    fun rawReport() {
        val rawTable = Table()
        rawTable.column("PID", 10)
                .column("USER", 20)
                .column("COMMAND", 40)
                .column("FILES", 20)

        rawTable.printHeading()
        rawTable.printRow()

        records
            .values
            .sortedByDescending { it.files.size }
            .forEach {
                rawTable.printRow(it.pid.toString(), it.user, it.command, it.files.size.toString())
            }
    }
}