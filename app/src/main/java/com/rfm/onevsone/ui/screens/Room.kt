@file:OptIn(ExperimentalMaterial3Api::class)

package com.rfm.onevsone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rfm.onevsone.domain.*
import com.rfm.onevsone.media.Sfx
import com.rfm.onevsone.net.HostServer
import com.rfm.onevsone.net.Link
import com.rfm.onevsone.net.Net
import com.rfm.onevsone.net.RoomSession
import com.rfm.onevsone.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RoomScreen(nav: NavController) {
    val c = container()
    val session = c.session
    if (session == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val snap by session.snapshot.collectAsStateWithLifecycle()
    val link by session.link.collectAsStateWithLifecycle()
    val err by session.error.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var lastRecorded by remember { mutableIntStateOf(0) }

    fun leave() {
        session.close()
        c.session = null
        nav.popBackStack()
    }
    BackHandler { confirmLeave = true }

    // تسجيل نتيجة كل مباراة أشارك فيها + الصوت
    LaunchedEffect(snap?.gameNo, snap?.phase) {
        val s = snap ?: return@LaunchedEffect
        if (s.phase == Phase.FINISHED && s.gameNo != lastRecorded) {
            lastRecorded = s.gameNo
            val me = session.myId
            val px = s.player(s.xId)
            val po = s.player(s.oId)
            if (me == s.xId || me == s.oId) {
                c.sound.play(
                    when {
                        s.winnerId < 0 -> Sfx.DRAW
                        s.winnerId == me -> Sfx.WIN
                        else -> Sfx.LOSE
                    }
                )
                c.sound.vibrate(120)
                if (px != null && po != null) {
                    val w = when {
                        s.winnerId < 0 -> 0
                        s.winnerId == s.xId -> 1
                        else -> 2
                    }
                    val rec = MatchRecord(
                        ts = System.currentTimeMillis(), mode = "ROOM",
                        p1 = px.name, p2 = po.name, a1 = px.avatar, a2 = po.avatar,
                        winner = w, mine = if (me == s.xId) 1 else 2
                    )
                    withContext(Dispatchers.IO) { c.repo.insertMatch(rec) }
                }
            }
        }
    }
    LaunchedEffect(snap?.board) {
        val s = snap
        if (s != null && s.phase == Phase.PLAYING && s.board.any { it != '.' }) c.sound.play(Sfx.CLICK)
    }
    LaunchedEffect(snap?.players?.size) { if ((snap?.players?.size ?: 0) > 1) c.sound.play(Sfx.JOIN) }

    ScreenScaffold("غرفة اللعب", { confirmLeave = true }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (link == Link.CLOSED) {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(err ?: "انتهت الجلسة", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Button(onClick = { leave() }) { Text("العودة") }
                    }
                }
            }
            val s = snap
            if (s == null) {
                CircularProgressIndicator()
                Text("جارٍ الاتصال…")
            } else {
                if (s.phase == Phase.LOBBY) Lobby(session, s) else MatchPanel(session, s)
                Scoreboard(s, session.myId)
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("مغادرة الغرفة؟") },
            text = { Text(if (session.isHost) "إذا غادرت كمضيف ستُغلق الغرفة لجميع اللاعبين." else "ستخرج من المباراة الجارية.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; leave() }) { Text("مغادرة") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("البقاء") } }
        )
    }
}

@Composable
private fun Lobby(session: RoomSession, s: RoomSnapshot) {
    val clipboard = LocalClipboardManager.current
    val host = session as? HostServer
    if (host != null) {
        val ips = remember { Net.localIps() }
        SectionCard {
            Column(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("كود الغرفة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(host.code, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { clipboard.setText(AnnotatedString(host.code)) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نسخ الكود")
                }
                Text(
                    "اطلب من أصدقائك الاتصال بنفس الشبكة/الهوتسبوت ثم الانضمام بهذا الكود.",
                    textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ips.isNotEmpty()) {
                    Text("عنوان جهازك: ${ips.joinToString("  |  ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("اللاعبون (${s.players.size}/6)", fontWeight = FontWeight.Bold)
            s.players.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.avatar, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(it.name + if (it.id == session.myId) " (أنت)" else "", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    if (session.isHost) {
        BigButton("ابدأ المباراة", enabled = s.players.size >= 2) { session.start() }
        if (s.players.size < 2) Text("بانتظار انضمام لاعب واحد على الأقل…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("بانتظار المضيف لبدء المباراة…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchPanel(session: RoomSession, s: RoomSnapshot) {
    val me = session.myId
    val px = s.player(s.xId)
    val po = s.player(s.oId)
    val playing = s.phase == Phase.PLAYING
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerBadge(px?.name ?: "—", px?.avatar ?: "", 'X', null, playing && s.turnId == s.xId, Modifier.weight(1f))
        PlayerBadge(po?.name ?: "—", po?.avatar ?: "", 'O', null, playing && s.turnId == s.oId, Modifier.weight(1f))
    }
    val status = when {
        s.phase == Phase.FINISHED -> if (s.winnerId < 0) "🤝 تعادل" else "🏆 فاز ${s.player(s.winnerId)?.name ?: ""}"
        s.turnId == me -> "دورك الآن!"
        else -> "دور ${s.player(s.turnId)?.name ?: ""}"
    }
    Text(status, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    BoardView(s.board, enabled = playing && s.turnId == me, winLine = s.winLine, onTap = { session.move(it) })
    if (me != s.xId && me != s.oId) {
        Text("أنت تشاهد المباراة الآن، دورك قريبًا 👀", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (s.queue.isNotEmpty()) {
        val names = s.queue.mapNotNull { s.player(it)?.let { p -> "${p.avatar} ${p.name}" } }
        Text("في الانتظار: ${names.joinToString(" ← ")}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (s.phase == Phase.FINISHED) BigButton("المباراة التالية") { session.next() }
}

@Composable
private fun Scoreboard(s: RoomSnapshot, me: Int) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("لوحة النقاط", fontWeight = FontWeight.Bold)
            s.players.sortedByDescending { it.points }.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.avatar, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.name + if (it.id == me) " (أنت)" else "", fontWeight = FontWeight.Medium)
                        Text("فوز ${it.wins} • تعادل ${it.draws} • خسارة ${it.losses}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${it.points}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
