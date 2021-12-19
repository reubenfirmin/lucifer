package model

const val UNKNOWN_USER = -1

/**
 * A record describing a process' state.
 */
data class ProcessRecord(var pid: Int = -1,
                         var parentPid: Int = -1,
                         var command: String = "",
                         var user: Int = UNKNOWN_USER,
                         var files: MutableList<FileRecord> = mutableListOf())
