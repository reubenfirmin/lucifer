package model

/**
 * A record describing a process' state.
 */
data class ProcessRecord(var pid: Int = 0,
                         var command: String = "",
                         var user: String = "",
                         var files: MutableList<FileRecord> = mutableListOf())
