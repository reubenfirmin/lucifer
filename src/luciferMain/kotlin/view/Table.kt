package view

class Table {

    data class Column(val heading: String, val width: Int, val prePadding: Int)

    private val columns = ArrayList<Column>()

    fun column(heading: String, width: Int = 20, prePadding: Int = 5): Table {
        columns.add(Column(heading, width, prePadding))
        return this
    }

    fun printHeading() {
        printRow(*columns.map { it.heading }.toTypedArray())
    }

    fun printRow(vararg data: String) {
        data.forEachIndexed { idx, item ->
            val column = columns[idx]
            if (column.prePadding >= 0) {
                print("".padEnd(column.prePadding, ' '))
            }
            print(if (item.length >= column.width) {
                item.substring(0, column.width)
            } else {
                item.padEnd(column.width, ' ')
            })
        }
        println()
    }
}