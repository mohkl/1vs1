package com.rfm.onevsone.domain

data class PlayerInfo(
    val id: Int,
    val name: String,
    val avatar: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
) {
    val points: Int get() = wins * 3 + draws
}

enum class Phase { LOBBY, PLAYING, FINISHED }

data class RoomSnapshot(
    val phase: Phase = Phase.LOBBY,
    val board: String = ".........",
    val xId: Int = -1,
    val oId: Int = -1,
    val turnId: Int = -1,
    val winnerId: Int = -1,
    val winLine: List<Int> = emptyList(),
    val gameNo: Int = 0,
    val players: List<PlayerInfo> = emptyList(),
    val queue: List<Int> = emptyList()
) {
    fun player(id: Int): PlayerInfo? = players.firstOrNull { it.id == id }
}

/**
 * محرك الغرفة (على جهاز المضيف فقط): يدير اللاعبين حتى 6 لاعبين.
 * نظام التناوب: الفائز يبقى، الخاسر يذهب لآخر الطابور، وفي التعادل يذهب لاعب X لآخر الطابور.
 */
class RoomEngine(private val maxPlayers: Int = 6) {
    private val players = LinkedHashMap<Int, PlayerInfo>()
    private val queue = ArrayDeque<Int>()
    private var nextId = 0
    private var phase = Phase.LOBBY
    private var board = "........."
    private var xId = -1
    private var oId = -1
    private var turnId = -1
    private var winnerId = -1
    private var winLine: List<Int> = emptyList()
    private var gameNo = 0

    fun add(name: String, avatar: String): Int? {
        if (players.size >= maxPlayers) return null
        val id = nextId++
        players[id] = PlayerInfo(id, name, avatar)
        queue.addLast(id)
        return id
    }

    fun remove(id: Int) {
        if (!players.containsKey(id)) return
        players.remove(id)
        queue.remove(id)
        if (phase == Phase.PLAYING && (id == xId || id == oId)) {
            val winner = if (id == xId) oId else xId
            finish(winner, emptyList())
        }
    }

    fun start(): Boolean {
        if (phase != Phase.LOBBY || queue.size < 2) return false
        xId = queue.removeFirst()
        oId = queue.removeFirst()
        begin()
        return true
    }

    private fun begin() {
        board = "........."
        turnId = xId
        winnerId = -1
        winLine = emptyList()
        phase = Phase.PLAYING
        gameNo++
    }

    fun move(playerId: Int, cell: Int): Boolean {
        if (phase != Phase.PLAYING || playerId != turnId) return false
        if (cell !in 0..8 || board[cell] != '.') return false
        val mark = if (playerId == xId) 'X' else 'O'
        board = Rules.place(board, cell, mark)
        val wl = Rules.winLine(board)
        if (wl != null) {
            finish(playerId, wl.toList())
        } else if (Rules.full(board)) {
            finish(-1, emptyList())
        } else {
            turnId = if (playerId == xId) oId else xId
        }
        return true
    }

    private fun finish(winner: Int, line: List<Int>) {
        phase = Phase.FINISHED
        winnerId = winner
        winLine = line
        turnId = -1
        if (winner >= 0) {
            val loser = if (winner == xId) oId else xId
            bump(winner) { it.copy(wins = it.wins + 1) }
            bump(loser) { it.copy(losses = it.losses + 1) }
        } else {
            bump(xId) { it.copy(draws = it.draws + 1) }
            bump(oId) { it.copy(draws = it.draws + 1) }
        }
    }

    private fun bump(id: Int, f: (PlayerInfo) -> PlayerInfo) {
        players[id]?.let { players[id] = f(it) }
    }

    fun nextRound() {
        if (phase != Phase.FINISHED) return
        val stayer: Int
        if (winnerId >= 0) {
            stayer = winnerId
            val loser = if (winnerId == xId) oId else xId
            if (players.containsKey(loser)) queue.addLast(loser)
        } else {
            if (players.containsKey(xId)) queue.addLast(xId)
            stayer = oId
        }
        val cand = ArrayList<Int>()
        val stayerAlive = players.containsKey(stayer)
        if (stayerAlive) cand.add(stayer)
        while (cand.size < 2 && queue.isNotEmpty()) cand.add(queue.removeFirst())
        if (cand.size < 2) {
            queue.addAll(0, cand)
            phase = Phase.LOBBY
            xId = -1; oId = -1; turnId = -1; winnerId = -1
            winLine = emptyList()
            board = "........."
            return
        }
        if (stayerAlive) {
            val wasX = stayer == xId
            if (wasX) { xId = cand[1]; oId = stayer } else { xId = stayer; oId = cand[1] }
        } else {
            xId = cand[0]; oId = cand[1]
        }
        begin()
    }

    fun snapshot(): RoomSnapshot = RoomSnapshot(
        phase, board, xId, oId, turnId, winnerId, winLine, gameNo,
        players.values.toList(), queue.toList()
    )
}

object Wire {
    fun clean(s: String, max: Int = 14): String {
        val t = s.replace(Regex("[|;~,\\r\\n]"), " ").trim().take(max)
        return t.ifEmpty { "لاعب" }
    }

    fun encode(s: RoomSnapshot): String {
        val pl = s.players.joinToString(";") {
            "${it.id}~${it.name}~${it.avatar}~${it.wins}~${it.losses}~${it.draws}"
        }
        val wl = if (s.winLine.isEmpty()) "-" else s.winLine.joinToString(",")
        val q = if (s.queue.isEmpty()) "-" else s.queue.joinToString(",")
        return listOf("ST", s.phase.name, s.board, s.xId, s.oId, s.turnId, s.winnerId, wl, s.gameNo, q, pl)
            .joinToString("|")
    }

    fun decode(line: String): RoomSnapshot? {
        return try {
            val p = line.split("|")
            if (p.size != 11 || p[0] != "ST") return null
            val players = if (p[10].isEmpty()) emptyList() else p[10].split(";").map {
                val f = it.split("~")
                PlayerInfo(f[0].toInt(), f[1], f[2], f[3].toInt(), f[4].toInt(), f[5].toInt())
            }
            RoomSnapshot(
                phase = Phase.valueOf(p[1]),
                board = p[2],
                xId = p[3].toInt(), oId = p[4].toInt(), turnId = p[5].toInt(), winnerId = p[6].toInt(),
                winLine = if (p[7] == "-") emptyList() else p[7].split(",").map { it.toInt() },
                gameNo = p[8].toInt(),
                queue = if (p[9] == "-") emptyList() else p[9].split(",").map { it.toInt() },
                players = players
            )
        } catch (e: Exception) {
            null
        }
    }
}
