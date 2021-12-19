package model

/**
 * A record describing a file associated with a process.
 */
data class FileRecord(val descriptor: String, val type: String, val name: String)
