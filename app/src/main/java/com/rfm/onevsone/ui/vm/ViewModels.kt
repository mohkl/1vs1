package com.rfm.onevsone.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfm.onevsone.App
import com.rfm.onevsone.domain.*
import com.rfm.onevsone.media.Sfx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class ProgressUi(
    val matches: List<MatchRecord> = emptyList(),
    val stats: Stats = Stats(0, 0, 0, 0, 0, 0),
    val completed: Set<String> = emptySet(),
    val leaderboard: List<LeaderRow> = emptyList(),
    val unlocked: Set<String> = emptySet(),
    val totalPoints: Int = 0
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as App).container
    private val _ui = MutableStateFlow(ProgressUi())
    val ui: StateFlow<ProgressUi> = _ui

    fun refresh() {
        viewModelScope.launch { _ui.value = withContext(Dispatchers.IO) { build() } }
    }

    private fun build(): ProgressUi {
        val m = c.repo.matches()
        val done = c.repo.completedChallenges()
        val p = c.settings.flow.value
        val st = Progress.stats(m)
        val ach = Progress.achievements.filter { it.check(st, m, done.size) }.map { it.id }.toSet()
        val total = st.matchPoints + p.challengePts + ach.size * 20
        return ProgressUi(m, st, done, Progress.leaderboard(m, p.name, p.challengePts), ach, total)
    }

    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { c.repo.clearAll() }
            c.settings.update { it.copy(challengePts = 0) }
            refresh()
        }
    }
}

data class GameUi(
    val cfg: GameConfig? = null,
    val board: String = ".........",
    val turn: Char = 'X',
    val over: Boolean = false,
    val winLine: List<Int> = emptyList(),
    val winnerSlot: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val draws: Int = 0,
    val p1Mark: Char = 'X',
    val thinking: Boolean = false,
    val resultText: String = "",
    val challengeNote: String = ""
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as App).container
    private val _ui = MutableStateFlow(GameUi())
    val ui: StateFlow<GameUi> = _ui
    private var cfg: GameConfig? = null
    private var round = 0
    private var humanMoves = 0
    private var aiJob: Job? = null

    init {
        c.pendingGame?.let {
            cfg = it
            _ui.value = GameUi(cfg = it)
            startRound()
        }
    }

    private fun startRound() {
        val g = cfg ?: return
        aiJob?.cancel()
        val p1Starts = when (g.fixedStarter) {
            1 -> true
            2 -> false
            else -> round % 2 == 0
        }
        humanMoves = 0
        _ui.value = _ui.value.copy(
            board = ".........", turn = 'X', over = false, winLine = emptyList(), winnerSlot = 0,
            p1Mark = if (p1Starts) 'X' else 'O', thinking = false, resultText = "", challengeNote = ""
        )
        aiMoveIfNeeded()
    }

    fun rematch() {
        round++
        startRound()
    }

    private fun isAiTurn(s: GameUi): Boolean {
        val g = cfg ?: return false
        if (g.mode == GameMode.LOCAL) return false
        return s.turn == Rules.other(s.p1Mark)
    }

    private fun aiMoveIfNeeded() {
        val s = _ui.value
        if (s.over || !isAiTurn(s)) return
        _ui.value = s.copy(thinking = true)
        aiJob = viewModelScope.launch {
            delay(450L + Random.nextInt(350))
            val st = _ui.value
            val aiMark = Rules.other(st.p1Mark)
            val level = cfg?.level ?: Difficulty.EASY
            val m = withContext(Dispatchers.Default) { Ai.move(st.board, aiMark, level) }
            _ui.value = _ui.value.copy(thinking = false)
            if (m >= 0) place(m)
        }
    }

    fun tap(i: Int) {
        val g = cfg ?: return
        val s = _ui.value
        if (s.over || s.thinking || isAiTurn(s) || s.board[i] != '.') return
        if (g.mode != GameMode.LOCAL) humanMoves++
        place(i)
    }

    private fun place(i: Int) {
        val s = _ui.value
        if (s.over || s.board[i] != '.') return
        val nb = Rules.place(s.board, i, s.turn)
        c.sound.play(if (s.turn == 'X') Sfx.X else Sfx.O)
        val wl = Rules.winLine(nb)
        when {
            wl != null -> finish(nb, s.turn, wl.toList())
            Rules.full(nb) -> finish(nb, null, emptyList())
            else -> {
                _ui.value = s.copy(board = nb, turn = Rules.other(s.turn))
                aiMoveIfNeeded()
            }
        }
    }

    private fun modeKey(g: GameConfig): String = when (g.mode) {
        GameMode.LOCAL -> "LOCAL"
        GameMode.AI -> "AI:${g.level.name}"
        GameMode.CHALLENGE -> "CH:${g.challenge?.id}:${g.level.name}"
    }

    private fun finish(board: String, winMark: Char?, line: List<Int>) {
        val s = _ui.value
        val g = cfg ?: return
        val slot = when {
            winMark == null -> 0
            winMark == s.p1Mark -> 1
            else -> 2
        }
        val vsComputer = g.mode != GameMode.LOCAL
        val text = when (slot) {
            1 -> "🏆 فاز ${g.p1Name}"
            2 -> if (vsComputer) "💻 فاز الكمبيوتر" else "🏆 فاز ${g.p2Name}"
            else -> "🤝 تعادل"
        }
        _ui.value = s.copy(
            board = board, over = true, winLine = line, winnerSlot = slot,
            s1 = s.s1 + if (slot == 1) 1 else 0,
            s2 = s.s2 + if (slot == 2) 1 else 0,
            draws = s.draws + if (slot == 0) 1 else 0,
            thinking = false, resultText = text
        )
        c.sound.play(
            when {
                slot == 0 -> Sfx.DRAW
                vsComputer && slot == 2 -> Sfx.LOSE
                else -> Sfx.WIN
            }
        )
        c.sound.vibrate(if (slot == 0) 60 else 140)
        val moves = humanMoves
        viewModelScope.launch(Dispatchers.IO) {
            c.repo.insertMatch(
                MatchRecord(
                    ts = System.currentTimeMillis(), mode = modeKey(g),
                    p1 = g.p1Name, p2 = g.p2Name, a1 = g.p1Avatar, a2 = g.p2Avatar,
                    winner = slot, mine = if (g.mode == GameMode.LOCAL) 0 else 1
                )
            )
            val ch = g.challenge
            if (ch != null) {
                val won = slot == 1
                val ok = when (ch.goal) {
                    Goal.WIN -> won
                    Goal.WIN_OR_DRAW -> won || slot == 0
                    Goal.WIN_FAST -> won && moves <= 3
                }
                val note = if (ok) {
                    val first = c.repo.completeChallenge(ch.id)
                    if (first) {
                        c.settings.update { it.copy(challengePts = it.challengePts + ch.points) }
                        "🎉 أنجزت التحدي! حصلت على ${ch.points} نقطة"
                    } else "✅ أنجزت التحدي (سبق أن أنجزته)"
                } else "لم يتحقق شرط التحدي، حاول مرة أخرى"
                _ui.value = _ui.value.copy(challengeNote = note)
            }
        }
    }
}
