package view

import io.IOHelpers.ansiUnderline
import kotlin.math.max

/**
 * TODO smart wrapping in table
 * @param formatting if true, use color to format
 * @param maxWidth terminal width (TODO doesn't fully obey this yet)
 */
class Table(val formatting: Boolean, val maxWidth: Int) {

    private interface Column {
        val prePadding: Int
            get() = 0
        val width: Int
            get() = 0
    }

    private class VisibleColumn(val heading: String,
                                val headingFormatter: (Int, String, String) -> String,
                                override val width: Int,
                                override val prePadding: Int,
                                val rowFormatter: (Int, String, String) -> String): Column

    private class Sink: Column

    private val columns = ArrayList<Column>()

    /**
     * Formatters - if specified, special formatting to apply to the provided padded value. Params are index, raw value,
     * padded/cropped value. The padded/cropped value should be the one that's output. Index / raw value are provided
     * to allow calculation of formatting rules.
     *
     * @param heading - column heading
     * @param headingFormatter - if specified, special formatting to apply to heading (underlined by default); provided
     *                          index / raw value / padded value
     * @param width - width to crop/pad the column to
     * @param prePadding - spaces to insert prior to the column
     * @param consumeRemainingWidth - can set to true on last column to fill width
     * @param rowFormatter - formatter for values in this row/column - provided index / raw value / padded value
     */
    fun column(heading: String,
               headingFormatter: (Int, String, String) -> String = { _, _, str -> ansiUnderline(str) },
               width: Int = 20,
               prePadding: Int = 5,
               consumeRemainingWidth: Boolean = false,
               rowFormatter: (Int, String, String) -> String = defaultFormatter): Table {

        val renderWidth = if (consumeRemainingWidth) {
            val usedWidth = columns.sumOf { it.prePadding + it.width } + prePadding
            // hide if less than 0
            max(maxWidth - usedWidth, 0)
        } else {
            width
        }

        columns.add(VisibleColumn(heading, headingFormatter, renderWidth, prePadding, rowFormatter))
        return this
    }

    /**
     * Adds a column if condition is true, otherwise adds a Sink. See [column]
     */
    fun optionalColumn(condition: Boolean,
                       heading: String,
                       headingFormatter: (Int, String, String) -> String = { _, _, str -> ansiUnderline(str) },
                       width: Int = 20,
                       prePadding: Int = 5,
                       consumeRemainingWidth: Boolean = false,
                       rowFormatter: (Int, String, String) -> String = defaultFormatter): Table {

        if (condition) {
            column(heading, headingFormatter, width, prePadding, consumeRemainingWidth, rowFormatter)
        } else {
            columns.add(Sink())
        }
        return this
    }

    val defaultFormatter: (Int, String, String) -> String = { _, _, str -> str }

    fun printHeading() {
        printRow(-1, true, *columns.map { if (it is VisibleColumn) { it.heading } else { "" } }.toTypedArray())
    }

    /**
     * If index is -1, row formatter will be skipped
     */
    fun printRow(index: Int, vararg data: String) {
        printRow(index, false, *data)
    }

    private fun printRow(rowIndex: Int, heading: Boolean = false, vararg data: String) {
        data.forEachIndexed { idx, item ->
            val column = columns[idx]
            if (column is VisibleColumn) {
                if (column.prePadding >= 0) {
                    print("".padEnd(column.prePadding, ' '))
                }

                val formatter = if (formatting) {
                    if (heading) {
                        column.headingFormatter
                    } else {
                        // don't format -1
                        if (rowIndex >= 0) {
                            column.rowFormatter
                        } else {
                            defaultFormatter
                        }
                    }
                } else {
                    defaultFormatter
                }

                print(
                    formatter.invoke(
                        rowIndex, item,
                        if (item.length >= column.width) {
                            item.substring(0, column.width)
                        } else {
                            item.padEnd(column.width, ' ')
                        }
                    )
                )
            }
        }
        println()
    }
}