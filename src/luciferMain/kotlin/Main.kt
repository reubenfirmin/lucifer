import io.IOHelpers.printErr
import io.IOHelpers.readLine
import view.ProgressSpinner

fun main(args: Array<String>) {
    val spinner = ProgressSpinner("Lucifer 0.2 - now reading from stdin")

    val bufferLen = 4096
    val buffer = ByteArray(bufferLen)

    // TODO brutal hack for now. there is an arg parser available in kotlinx, but it adds 1MB to the compiled binary
    val debug = args.contains("--debug")
    val err = args.contains("--err")
    val noformat = args.contains("--noformat")
    val processes = args.filter {
        it.startsWith("--process=")
    }.map {
        it.substring(10)
    }

    val parser = LSOFParser(debug)

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

        if (err && line != null) {
            printErr(line)
        }

        if ((lines++ % 1000) == 0) {
            spinner.spin()
        }

        if (!parser.parseLine(line)) {
            spinner.clear()
            val reporter = LSOFReporter(UserResolver(buffer), parser.yieldData(), !noformat, buffer)
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

