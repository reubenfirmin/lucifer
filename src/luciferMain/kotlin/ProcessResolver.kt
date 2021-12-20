import io.IOHelpers.runCommand
import model.ProcessMetadata

/**
 * Attempts to get additional metadata about processes for optional display
 */
class ProcessResolver(buffer: ByteArray) {

    private val processes: Map<Int, ProcessMetadata>

    init {
        processes = try {
             runCommand("ps -e -o pid,%cpu,%mem,cputimes,args --cols 500 --no-headers", buffer)
                .split("\n")
                .map {
                    it.split(" ")
                        .filterNot { part -> part.trim().isEmpty() }
                }.map {
                    ProcessMetadata(it[0].toInt(), it[1].toDouble(), it[2].toDouble(), it[3].toInt(),
                        it.subList(4, it.size).joinToString(" "))
                }.associateBy { it.pid }
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf()
        }
    }

    fun initialized() = processes.size > 0

    fun process(pid: Int) = processes[pid]
}
