package com.rfm.onevsone.domain

enum class GameMode { LOCAL, AI, CHALLENGE }

enum class Goal { WIN, WIN_OR_DRAW, WIN_FAST }

class Challenge(
    val id: String,
    val title: String,
    val desc: String,
    val level: Difficulty,
    val goal: Goal,
    val aiStarts: Boolean,
    val points: Int
)

object Challenges {
    val ALL = listOf(
        Challenge("c1", "البداية", "افز على الكمبيوتر (سهل)", Difficulty.EASY, Goal.WIN, false, 10),
        Challenge("c2", "خاطف", "افز على (سهل) في 3 حركات فقط", Difficulty.EASY, Goal.WIN_FAST, false, 15),
        Challenge("c3", "المتوسطون", "افز على الكمبيوتر (متوسط)", Difficulty.MEDIUM, Goal.WIN, false, 20),
        Challenge("c4", "هو يبدأ", "افز على (متوسط) والكمبيوتر هو من يبدأ", Difficulty.MEDIUM, Goal.WIN, true, 30),
        Challenge("c5", "سرعة البرق", "افز على (متوسط) في 3 حركات فقط", Difficulty.MEDIUM, Goal.WIN_FAST, false, 35),
        Challenge("c6", "المواجهة الصعبة", "افز على الكمبيوتر (صعب)", Difficulty.HARD, Goal.WIN, false, 45),
        Challenge("c7", "الصعب يبدأ", "افز على (صعب) والكمبيوتر هو من يبدأ", Difficulty.HARD, Goal.WIN, true, 60),
        Challenge("c8", "الجدار الأخير", "تعادل أو افز على الكمبيوتر (خبير)", Difficulty.EXPERT, Goal.WIN_OR_DRAW, false, 80)
    )
}

data class GameConfig(
    val mode: GameMode,
    val p1Name: String,
    val p1Avatar: String,
    val p2Name: String,
    val p2Avatar: String,
    val level: Difficulty = Difficulty.EASY,
    /** 0 = بالتناوب، 1 = اللاعب الأول دائمًا، 2 = اللاعب الثاني (الكمبيوتر) دائمًا */
    val fixedStarter: Int = 0,
    val challenge: Challenge? = null
)

data class MatchRecord(
    val id: Long = 0,
    val ts: Long,
    val mode: String,
    val p1: String,
    val p2: String,
    val a1: String,
    val a2: String,
    /** 0 تعادل، 1 فاز p1، 2 فاز p2 */
    val winner: Int,
    /** 0 لعب محلي بين شخصين، 1 أو 2 = جانبي أنا */
    val mine: Int
)

data class LeaderRow(
    val name: String, val avatar: String,
    val wins: Int, val losses: Int, val draws: Int, val points: Int
)

data class Stats(
    val played: Int, val wins: Int, val losses: Int, val draws: Int,
    val bestStreak: Int, val matchPoints: Int
)

class Achievement(
    val id: String,
    val icon: String,
    val title: String,
    val desc: String,
    val check: (Stats, List<MatchRecord>, Int) -> Boolean
)

object Avatars {
    /** (الرمز، النقاط المطلوبة لفتحه) */
    val ALL = listOf(
        "😎" to 0, "🙂" to 0, "😺" to 0, "🐶" to 0, "🦊" to 0, "🐼" to 0,
        "🤖" to 60, "🦁" to 120, "🐯" to 200, "🐲" to 300, "🥷" to 450,
        "🚀" to 600, "👑" to 800, "🔥" to 1000
    )
}

object Progress {
    val achievements = listOf(
        Achievement("first", "🥇", "أول فوز", "افز بأول مباراة لك") { s, _, _ -> s.wins >= 1 },
        Achievement("w10", "🔟", "عشرة انتصارات", "حقق 10 انتصارات") { s, _, _ -> s.wins >= 10 },
        Achievement("w50", "🏅", "محارب", "حقق 50 انتصارًا") { s, _, _ -> s.wins >= 50 },
        Achievement("p100", "🎮", "محترف", "العب 100 مباراة") { s, _, _ -> s.played >= 100 },
        Achievement("streak", "🔥", "سلسلة نارية", "حقق 5 انتصارات متتالية") { s, _, _ -> s.bestStreak >= 5 },
        Achievement("expert", "🧠", "تحدي العقل", "تعادل أو افز على الكمبيوتر الخبير") { _, m, _ ->
            m.any { it.mine > 0 && it.mode.contains("EXPERT") && (it.winner == 0 || it.winner == it.mine) }
        },
        Achievement("room", "🌐", "لاعب جماعي", "العب مباراة داخل غرفة") { _, m, _ ->
            m.any { it.mode == "ROOM" && it.mine > 0 }
        },
        Achievement("chall", "🏆", "سيد التحديات", "أنجز جميع التحديات") { _, _, c -> c >= Challenges.ALL.size }
    )

    fun stats(all: List<MatchRecord>): Stats {
        val mine = all.filter { it.mine > 0 }.sortedBy { it.ts }
        var streak = 0; var best = 0; var w = 0; var l = 0; var d = 0
        for (r in mine) {
            when {
                r.winner == 0 -> { d++; streak = 0 }
                r.winner == r.mine -> { w++; streak++; if (streak > best) best = streak }
                else -> { l++; streak = 0 }
            }
        }
        return Stats(mine.size, w, l, d, best, w * 3 + d)
    }

    private class Acc(var av: String, var w: Int = 0, var l: Int = 0, var d: Int = 0)

    fun leaderboard(all: List<MatchRecord>, myName: String, challengePts: Int): List<LeaderRow> {
        val map = LinkedHashMap<String, Acc>()
        fun acc(n: String, a: String): Acc {
            val x = map.getOrPut(n) { Acc(a) }
            x.av = a
            return x
        }
        for (r in all.sortedBy { it.ts }) {
            val vsComputer = r.mode.startsWith("AI") || r.mode.startsWith("CH")
            val a1 = acc(r.p1, r.a1)
            val a2 = if (vsComputer) null else acc(r.p2, r.a2)
            when (r.winner) {
                0 -> { a1.d++; a2?.let { it.d++ } }
                1 -> { a1.w++; a2?.let { it.l++ } }
                else -> { a1.l++; a2?.let { it.w++ } }
            }
        }
        if (challengePts > 0 && !map.containsKey(myName)) map[myName] = Acc("😎")
        return map.map { (n, a) ->
            LeaderRow(n, a.av, a.w, a.l, a.d, a.w * 3 + a.d + if (n == myName) challengePts else 0)
        }.sortedByDescending { it.points }
    }
}
