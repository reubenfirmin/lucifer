import io.IOHelpers.printErr
import io.IOHelpers.readLine
import model.*
import view.ProgressSpinner

fun main(args: Array<String>) {
    val spinner = ProgressSpinner("Lucifer 0.1 - now reading from stdin")

    val bufferLen = 4096
    val buffer = ByteArray(bufferLen)

    // TODO brutal hack for now. there is an arg parser available in kotlinx, but it adds 500KB to the compiled binary!
    val debug = args.contains("--debug")
    val err = args.contains("--err")
    val noformat = args.contains("--noformat")
    val processes = args.filter {
        it.startsWith("--process=")
    }.map {
        it.substring(10)
    }
    val curses = args.contains("--curses")

    val parser = LSOFParser(debug)
    var lineState: Pair<ParseState, ProcessRecord?> = ParseState.new() to null

    // General algorithm:
    //
    // Read a line at a time until EOF.
    //
    // Every time we hit a line starting with "p" (pid / process id), then store the record we've been accumulating in
    // lineState, and start storing a new record.
    //
    // Records are variable length, because there is a 1:N relationship between processes and files (i.e. files are
    // listed within the overall process record.)
    var lines = 0
    while (true) {
        val line = readLine(buffer)
        if (line != null) {
            if (err) {
                printErr(line)
            }

            if ((lines++ % 1000) == 0) {
                spinner.spin()
            }

            lineState = parser.parseLine(line, lineState.first)

            if (lineState.second != null) {
                if (!lineState.first.initialized) {
                    throw Exception("ERROR - trying to add record that wasn't initialized")
                }
                parser.storeRecord(lineState.second!!)
            }
        } else {
            val last = parser.finish(lineState.first)
            if (last != null) {
                parser.storeRecord(last)
            }

            spinner.clear()

            if (curses) {

            } else {
                val reporter = LSOFReporter(UserResolver(buffer), parser.yieldData(), !noformat)
                // summarization mode
                if (processes.isEmpty()) {
                    reporter.byProcessReport()
                    println()
                    reporter.fileTypeUserReport()
                    println()
                    reporter.networkConnectionsReport()
                    // detail mode for specific processes
                } else {
                    processes.forEach {
                        reporter.processReport(it.toInt())
                        println()
                    }
                }
                break
            }
        }
    }
}

