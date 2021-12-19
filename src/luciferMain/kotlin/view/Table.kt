package view

import io.IOHelpers.ansiUnderline

class Table() {

    data class Column(val heading: String, val headingFormatter: (Int, String, String) -> String,
                      val width: Int, val prePadding: Int, val rowFormatter: (Int, String, String) -> String)

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
     * @param rowFormatter - formatter for values in this row/column - provided index / raw value / padded value
     */
    fun column(heading: String,
               headingFormatter: (Int, String, String) -> String = { _, _, str -> ansiUnderline(str) },
               width: Int = 20,
               prePadding: Int = 5,
               rowFormatter: (Int, String, String) -> String = defaultFormatter): Table {

        columns.add(Column(heading, headingFormatter, width, prePadding, rowFormatter))
        return this
    }

    val defaultFormatter: (Int, String, String) -> String = { _, _, str -> str }

    fun printHeading() {
        printRow(-1, true, *columns.map { it.heading }.toTypedArray())
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
            if (column.prePadding >= 0) {
                print("".padEnd(column.prePadding, ' '))
            }

            val formatter = if (heading) {
                column.headingFormatter
            } else {
                if (rowIndex >= 0) {
                    column.rowFormatter
                } else {
                    defaultFormatter
                }
            }

            print(formatter.invoke(rowIndex, item,
                if (item.length >= column.width) {
                    item.substring(0, column.width)
                } else {
                    item.padEnd(column.width, ' ')
                }
            ))
        }
        println()
    }
}