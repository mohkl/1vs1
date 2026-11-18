package com.rfm.onevsone.domain

import java.util.Random

enum class Difficulty(val label: String) {
    EASY("سهل"), MEDIUM("متوسط"), HARD("صعب"), EXPERT("خبير")
}

object Rules {
    val LINES = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
    )

    fun winLine(b: String): IntArray? {
        for (l in LINES) {
            val c = b[l[0]]
            if (c != '.' && c == b[l[1]] && c == b[l[2]]) return l
        }
        return null
    }

    fun winner(b: String): Char? = winLine(b)?.let { b[it[0]] }
    fun full(b: String): Boolean = b.none { it == '.' }
    fun place(b: String, i: Int, c: Char): String = b.substring(0, i) + c + b.substring(i + 1)
    fun other(c: Char): Char = if (c == 'X') 'O' else 'X'
}

object Ai {
    private val rnd = Random()

    fun move(board: String, me: Char, level: Difficulty): Int {
        val empty = board.indices.filter { board[it] == '.' }
        if (empty.isEmpty()) return -1
        return when (level) {
            Difficulty.EASY -> empty.random()
            Difficulty.MEDIUM -> heuristic(board, me) ?: empty.random()
            Difficulty.HARD ->
                if (rnd.nextInt(100) < 20) (heuristic(board, me) ?: empty.random()) else best(board, me)
            Difficulty.EXPERT -> best(board, me)
        }
    }

    private fun findWinning(board: String, c: Char): Int? {
        for (i in board.indices) {
            if (board[i] == '.' && Rules.winner(Rules.place(board, i, c)) == c) return i
        }
        return null
    }

    private fun heuristic(board: String, me: Char): Int? {
        findWinning(board, me)?.let { return it }
        findWinning(board, Rules.other(me))?.let { return it }
        return null
    }

    private fun best(board: String, me: Char): Int {
        val empty = board.indices.filter { board[it] == '.' }
        if (empty.size == 9) return listOf(0, 2, 4, 6, 8).random()
        var bestScore = -100
        val bests = ArrayList<Int>()
        for (i in empty) {
            val s = minimax(Rules.place(board, i, me), Rules.other(me), me, 1)
            if (s > bestScore) { bestScore = s; bests.clear(); bests.add(i) }
            else if (s == bestScore) bests.add(i)
        }
        return bests.random()
    }

    private fun minimax(b: String, turn: Char, me: Char, depth: Int): Int {
        val w = Rules.winner(b)
        if (w == me) return 10 - depth
        if (w != null) return depth - 10
        if (Rules.full(b)) return 0
        var res = if (turn == me) -100 else 100
        for (i in b.indices) {
            if (b[i] != '.') continue
            val s = minimax(Rules.place(b, i, turn), Rules.other(turn), me, depth + 1)
            res = if (turn == me) maxOf(res, s) else minOf(res, s)
        }
        return res
    }
}
