package model

/**
 * A record describing a process' state.
 */
data class ProcessRecord(var pid: Int = -1,
                         var parentPid: Int = -1,
                         var command: String = "",
                         var user: Int = -1,
                         var files: MutableList<FileRecord> = mutableListOf())
