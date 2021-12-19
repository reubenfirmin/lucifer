package view

import io.IOHelpers.ansiUnderline

class Table() {

    data class Column(val heading: String, val headingFormatter: (String) -> String,
                      val width: Int, val prePadding: Int, val rowFormatter: (String) -> String)

    private val columns = ArrayList<Column>()

    fun column(heading: String,
               headingFormatter: (String) -> String = { str -> ansiUnderline(str) },
               width: Int = 20,
               prePadding: Int = 5,
               rowFormatter: (String) -> String = {it}): Table {

        columns.add(Column(heading, headingFormatter, width, prePadding, rowFormatter))
        return this
    }

    fun printHeading() {
        printRow(true, *columns.map { it.heading }.toTypedArray())
    }

    fun printRow(vararg data: String) {
        printRow(false, *data)
    }

    private fun printRow(heading: Boolean = false, vararg data: String) {
        data.forEachIndexed { idx, item ->
            val column = columns[idx]
            if (column.prePadding >= 0) {
                print("".padEnd(column.prePadding, ' '))
            }

            val formatter = if (heading) { column.headingFormatter } else { column.rowFormatter }

            print(formatter.invoke(
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