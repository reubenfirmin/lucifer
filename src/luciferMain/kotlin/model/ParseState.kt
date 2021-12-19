package model

/**
 * A mutable state object that can be passed around when iterating through the output.
 */
data class ParseState(var record: ProcessRecord,
                      var initialized: Boolean,
                      var descriptor: String?,
                      var type: String?,
                      var name: String?) {

    companion object {
        fun new() = ParseState(ProcessRecord(), false, null, null, null)
    }
}