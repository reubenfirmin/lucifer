package view

import io.IOHelpers.ansiClear
import io.IOHelpers.ansiOverwrite

class ProgressSpinner(val title: String) {

    val states = arrayOf('-', '/', '|', '\\')
    var idx = 0

    fun spin() {
        idx++
        if (idx > states.size - 1) {
            idx = 0
        }
        print(ansiOverwrite("$title ${states[idx]}"))
    }

    fun clear() {
        print(ansiClear())
        print(ansiOverwrite(""))
    }
}