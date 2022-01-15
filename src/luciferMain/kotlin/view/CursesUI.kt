package view

import kotlinx.cinterop.CPointer
import ncurses.*

class CursesUI {

    val sqWidth = 10
    val sqHeight = 10

    val board = mutableListOf<CPointer<WINDOW>?>()

    fun show() {
        initscr()
        noecho()
        cbreak()
        refresh()

        var starty = 0
        for (i in 0..10) {
            board.add(newwin(sqHeight, sqWidth, starty, i * sqWidth))
        }
        starty = sqHeight
        for (i in 0..10) {
            board.add(newwin(sqHeight, sqWidth, starty, i * sqWidth))
        }
        starty = sqHeight * 2
        for (i in 0..10) {
            board.add(newwin(sqHeight, sqWidth, starty, i * sqWidth))
        }

        for (window in board) {
            if (window == null) {
                println("Window was null!!")
            } else {
                box(window, 0, 0);
                wrefresh(window);
            }
        }

        getch()
    }
}