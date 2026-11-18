@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rfm.onevsone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rfm.onevsone.domain.*
import com.rfm.onevsone.net.ClientConnection
import com.rfm.onevsone.net.HostServer
import com.rfm.onevsone.net.Link
import com.rfm.onevsone.ui.*
import com.rfm.onevsone.ui.vm.ProgressViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalSetupScreen(nav: NavController) {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    val pvm: ProgressViewModel = viewModel()
    val pu by pvm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { pvm.refresh() }
    var n1 by remember { mutableStateOf(prefs.name) }
    var a1 by remember { mutableStateOf(prefs.avatar) }
    var n2 by remember { mutableStateOf("اللاعب 2") }
    var a2 by remember { mutableStateOf("🐶") }

    ScreenScaffold("اللعب المحلي", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileEditor("اللاعب الأول", n1, a1, pu.totalPoints, { n1 = it }, { a1 = it })
            ProfileEditor("اللاعب الثاني", n2, a2, pu.totalPoints, { n2 = it }, { a2 = it })
            Text("يتناوب اللاعبان على البدء في كل جولة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("ابدأ اللعب") {
                val name1 = n1.trim().ifEmpty { "اللاعب 1" }
                c.settings.update { it.copy(name = name1, avatar = a1) }
                c.pendingGame = GameConfig(GameMode.LOCAL, name1, a1, n2.trim().ifEmpty { "اللاعب 2" }, a2)
                nav.navigate("game")
            }
        }
    }
}

@Composable
fun AiSetupScreen(nav: NavController) {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    val pvm: ProgressViewModel = viewModel()
    val pu by pvm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { pvm.refresh() }
    var n1 by remember { mutableStateOf(prefs.name) }
    var a1 by remember { mutableStateOf(prefs.avatar) }
    var level by remember { mutableStateOf(Difficulty.MEDIUM) }
    var starter by remember { mutableIntStateOf(1) }

    ScreenScaffold("اللعب ضد الكمبيوتر", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileEditor("لاعب", n1, a1, pu.totalPoints, { n1 = it }, { a1 = it })
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مستوى الصعوبة", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.values().forEach { d ->
                            FilterChip(selected = level == d, onClick = { level = d }, label = { Text(d.label) })
                        }
                    }
                    Text("من يبدأ؟", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "أنا", 2 to "الكمبيوتر", 0 to "بالتناوب").forEach { (v, t) ->
                            FilterChip(selected = starter == v, onClick = { starter = v }, label = { Text(t) })
                        }
                    }
                }
            }
            BigButton("ابدأ اللعب") {
                val name1 = n1.trim().ifEmpty { "لاعب" }
                c.settings.update { it.copy(name = name1, avatar = a1) }
                c.pendingGame = GameConfig(GameMode.AI, name1, a1, "الكمبيوتر", "🤖", level, starter)
                nav.navigate("game")
            }
        }
    }
}

@Composable
fun CreateRoomScreen(nav: NavController) {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    val pvm: ProgressViewModel = viewModel()
    val pu by pvm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { pvm.refresh() }
    var n1 by remember { mutableStateOf(prefs.name) }
    var a1 by remember { mutableStateOf(prefs.avatar) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ScreenScaffold("إنشاء مباراة", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileEditor("ملفك كمضيف", n1, a1, pu.totalPoints, { n1 = it }, { a1 = it })
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "سيتم إنشاء غرفة بكود خاص. يتصل اللاعبون (حتى 6 بمن فيهم أنت) عبر نفس شبكة Wi-Fi أو " +
                            "هوتسبوت جهازك — لا حاجة للإنترنت. يلعب اثنان في كل مرة والباقون ينتظرون دورهم.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(if (busy) "جارٍ الإنشاء…" else "إنشاء الغرفة", enabled = !busy) {
                busy = true
                err = null
                scope.launch {
                    c.session?.close()
                    c.session = null
                    val name1 = n1.trim().ifEmpty { "لاعب" }
                    c.settings.update { it.copy(name = name1, avatar = a1) }
                    val host = HostServer()
                    val ok = withContext(Dispatchers.IO) { host.open(name1, a1) }
                    if (ok) {
                        c.session = host
                        nav.navigate("room") { popUpTo("create") { inclusive = true } }
                    } else {
                        err = host.error.value ?: "تعذر إنشاء الغرفة"
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
fun JoinRoomScreen(nav: NavController) {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    val pvm: ProgressViewModel = viewModel()
    val pu by pvm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { pvm.refresh() }
    var n1 by remember { mutableStateOf(prefs.name) }
    var a1 by remember { mutableStateOf(prefs.avatar) }
    var code by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var showIp by remember { mutableStateOf(false) }
    var conn by remember { mutableStateOf<ClientConnection?>(null) }
    val idleLink = remember { MutableStateFlow(Link.CLOSED) }
    val idleErr = remember { MutableStateFlow<String?>(null) }

    ScreenScaffold("الانضمام لمباراة", { conn?.close(); nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileEditor("ملفك", n1, a1, pu.totalPoints, { n1 = it }, { a1 = it })
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("كود الغرفة (6 أرقام)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showIp = !showIp }) { Text("خيارات متقدمة (إدخال IP المضيف يدويًا)") }
                    if (showIp) {
                        OutlinedTextField(
                            value = ip, onValueChange = { ip = it.trim() },
                            label = { Text("عنوان IP للمضيف (اختياري)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            val cc = conn
            val link by (cc?.link ?: idleLink).collectAsStateWithLifecycle()
            val err by (cc?.error ?: idleErr).collectAsStateWithLifecycle()
            LaunchedEffect(link, cc) {
                if (cc != null && link == Link.ONLINE) {
                    c.session = cc
                    nav.navigate("room") { popUpTo("join") { inclusive = true } }
                }
            }
            if (cc != null && link == Link.CONNECTING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("جارٍ البحث عن الغرفة…")
                }
            }
            if (cc != null) err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val searching = cc != null && link == Link.CONNECTING
            BigButton("انضمام", enabled = code.length == 6 && !searching) {
                conn?.close()
                val name1 = n1.trim().ifEmpty { "لاعب" }
                c.settings.update { it.copy(name = name1, avatar = a1) }
                val nc = ClientConnection()
                conn = nc
                nc.connect(code, name1, a1, ip)
            }
        }
    }
}
