package io

import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.*

object IOHelpers {

    val ESC = Char(27)

    /**
     * Print to stderr.
     */
    fun printErr(message: String) {
        fprintf(stderr!!, message + "\n")
        fflush(stderr!!)
    }

    /**
     * Replace kotlin's impl of readLine, which is broken on native (https://youtrack.jetbrains.com/issue/KT-39495)
     * TODO consider using readln() instead (added in 1.6) (test)
     * NOTE - not threadsafe
     * @return null if we hit EOF
     */
    fun readLine(buffer: ByteArray, allocated: Int): String? {
        val read = fgets(buffer.refTo(0), allocated, stdin)?.toKString() ?: return null
        return read.substring(0, read.length - 1)
    }

    private fun ansi(command: String, tail: String = "") = "$ESC[$command$tail"

    /**
     * Clears the current line
     */
    fun ansiClear() = ansi("2K")

    /**
     * Resets cursor to column 0 and writes whatever's provided to the line (does not clear)
     */
    fun ansiOverwrite(content: String) = ansi("0G", content)

    fun ansiBold(content: String) = ansi("1m", content) + ansi("22m")

    fun ansiItalic(content: String) = ansi("3m", content) + ansi("23m")

    fun ansiUnderline(content: String) = ansi("4m", content) + ansi("24m")

    fun ansiFg(fg: Color, content: String) = ansi("38;5;${fg.hex}m", content) + ansi("39m")

    fun ansiBg(bg: Color, content: String) = ansi("48;5;${bg.hex}m", content) + ansi("39m")

    fun ansiColor(fg: Color, bg: Color, content: String) = ansiFg(fg, ansiBg(bg, content))
}

enum class Color(val hex: Int) {
    BLACK(0),
    SCALE1_0(45), // TODO terminal color codes are terrible. possible to algorithmically pick an aesthetic range
    SCALE1_1(44),
    SCALE1_2(43),
    SCALE1_3(42),
    SCALE1_4(41),
    SCALE1_5(40);

    companion object {
        val scale1 = arrayListOf(SCALE1_0, SCALE1_1, SCALE1_2, SCALE1_3, SCALE1_4, SCALE1_5)
    }
}