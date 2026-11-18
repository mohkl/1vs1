package com.rfm.onevsone.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rfm.onevsone.domain.MatchRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "onevsone.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE matches(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, mode TEXT, " +
                "p1 TEXT, p2 TEXT, a1 TEXT, a2 TEXT, winner INTEGER, mine INTEGER)"
        )
        db.execSQL("CREATE TABLE challenges(id TEXT PRIMARY KEY, ts INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

class Repository(private val db: Db) {
    fun insertMatch(r: MatchRecord) {
        val v = ContentValues().apply {
            put("ts", r.ts); put("mode", r.mode)
            put("p1", r.p1); put("p2", r.p2); put("a1", r.a1); put("a2", r.a2)
            put("winner", r.winner); put("mine", r.mine)
        }
        db.writableDatabase.insert("matches", null, v)
    }

    fun matches(): List<MatchRecord> {
        val out = ArrayList<MatchRecord>()
        db.readableDatabase.rawQuery(
            "SELECT id,ts,mode,p1,p2,a1,a2,winner,mine FROM matches ORDER BY ts DESC LIMIT 500", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    MatchRecord(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getInt(7), c.getInt(8)
                    )
                )
            }
        }
        return out
    }

    fun completedChallenges(): Set<String> {
        val out = HashSet<String>()
        db.readableDatabase.rawQuery("SELECT id FROM challenges", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    /** يعيد true إذا كان هذا أول إنجاز لهذا التحدي */
    fun completeChallenge(id: String): Boolean {
        val v = ContentValues().apply { put("id", id); put("ts", System.currentTimeMillis()) }
        return db.writableDatabase.insertWithOnConflict(
            "challenges", null, v, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun clearAll() {
        db.writableDatabase.delete("matches", null, null)
        db.writableDatabase.delete("challenges", null, null)
    }
}

data class Prefs(
    val theme: Int = 0,           // 0 النظام، 1 فاتح، 2 داكن
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val name: String = "لاعب",
    val avatar: String = "😎",
    val rating: Int = 0,
    val challengePts: Int = 0
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Prefs> = _flow

    private fun load() = Prefs(
        sp.getInt("theme", 0),
        sp.getBoolean("sound", true),
        sp.getBoolean("vibrate", true),
        sp.getString("name", "لاعب") ?: "لاعب",
        sp.getString("avatar", "😎") ?: "😎",
        sp.getInt("rating", 0),
        sp.getInt("cpts", 0)
    )

    fun update(f: (Prefs) -> Prefs) {
        synchronized(this) {
            val n = f(_flow.value)
            _flow.value = n
            sp.edit()
                .putInt("theme", n.theme).putBoolean("sound", n.sound).putBoolean("vibrate", n.vibrate)
                .putString("name", n.name).putString("avatar", n.avatar)
                .putInt("rating", n.rating).putInt("cpts", n.challengePts)
                .apply()
        }
    }
}
