import io.IOHelpers.printErr
import io.IOHelpers.readLine
import model.*

fun main(args: Array<String>) {
    println("Lucifer - parsing input...")

    // TODO brutal hack for now. there is an arg parser available in kotlinx, but it adds 1MB to the compiled binary
    val debug = args.contains("--debug")
    val err = args.contains("--err")

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
    while (true) {
        val line = readLine()
        if (line != null) {
            if (err) {
                printErr(line)
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

            parser.printReport()
            println()
            println("DONE")
            break
        }
    }
}

