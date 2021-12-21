package view

import io.Color
import io.Color.*
import io.IOHelpers.ansiColor
import io.IOHelpers.ansiFg
import io.IOHelpers.ansiUnderline
import io.IOHelpers.runCommand
import service.ProcessesQuerier
import kotlin.math.min

/**
 * Generate various reports from the data.
 */
class LuciferReporter(
    private val querier: ProcessesQuerier,
    private val formatting: Boolean,
    buffer: ByteArray) {

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
        val bucketSize = (querier.numRecords()) / fileSizeColors

        /**
         * Map an item to a color
         */
        fun rangeColor(idx: Int) = Color.scale1[min((idx / bucketSize), fileSizeColors - 1)]

        val parents = querier.topNParents(Color.highlights.size)
            .map { it.index to it.value.toString() }
            .associate { it.second to Color.highlights[it.first] }

        val wide = terminalWidth > 100

        val parentPidFormatter: (Int, String, String) -> String = { _, raw, padded ->

            // look up the string version of the pid to see if it's one of the most popular parents
            val parentCol = parents[raw]

            if (parentCol != null) {
                // it's ugly to highlight the padding, so calculate how much padding was added and re-add
                // TODO probably want to change the formatted interface to make this less redundant
                val padding = " ".repeat(padded.length - raw.length)
                ansiColor(BLACK, parentCol, raw) + padding
            } else {
                padded
            }
        }

        val rawTable = Table(formatting, terminalWidth)
                .column("PARENT PID", width = 10, rowFormatter = parentPidFormatter)
                .column("PID", width = 10, rowFormatter = parentPidFormatter)
                .column("USER", width = 20)
                .column("FILES", width = 8) { idx, _, size ->
                    ansiFg(rangeColor(idx), size)
                }
                .optionalColumn(wide, "CPU", width = 5) // TODO generalize color ranges on these
                .optionalColumn(wide, "MEM", width = 5)
                .optionalColumn(wide, "TIME", width = 8)
                .column("COMMAND", consumeRemainingWidth = true)

        rawTable.printHeading()

        querier.processesByFileSizeWithMetadata().forEach { (idx, process) ->
            process.apply {
                rawTable.printRow(idx,
                    record.parentPid.toString(),
                    record.pid.toString(),
                    userName,
                    record.files.size.toString(),
                    metadata?.cpu?.toString() ?: "",
                    metadata?.memory?.toString() ?: "",
                    metadata?.cpuTime?.toString() ?: "",
                    metadata?.command ?: record.command
                )
            }
        }
    }

    fun fileTypeUserReport() {
        reportHeading("OPEN FILES BY TYPE BY USER")


        val userTypeTable = Table(formatting, terminalWidth)
            .column("USER")
            .column("TYPE")
            .column("COUNT")

        userTypeTable.printHeading()

        var index = 0

        querier.recordsByUserByTypes().forEach {  userSet ->
            userSet.second.forEach { typesSet ->
                userTypeTable.printRow(index++,
                    userSet.first,
                    typesSet.first,
                    typesSet.second.toString())
            }
        }
    }

    fun networkConnectionsReport() {
        reportHeading("INTERNET CONNECTIONS BY USER")

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
        querier.internetConnectionsByUser().forEach { userSet ->
            userSet.second.forEach { processSet ->
                processSet.second.forEach { fileSet ->
                    networkTable.printRow(index++,
                        userSet.first,
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

        val processRecord = querier.processWithMetadata(pid)

        if (processRecord == null) {
            println("Process $pid not found")
        } else {
            processRecord.apply {
                processTable.printRow(0,
                    record.parentPid.toString(),
                    record.pid.toString(),
                    userName,
                    metadata?.command ?: record.command
                )
            }

            println()
            val fileTable = Table(formatting, terminalWidth)
                .column("DESCRIPTOR", width = 10)
                .column("TYPE", width = 10)
                .column("PROTO", width = 6)
                .column("NAME", consumeRemainingWidth = true)

            fileTable.printHeading()

            processRecord.record.files
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