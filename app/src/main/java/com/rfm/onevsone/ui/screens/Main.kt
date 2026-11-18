@file:OptIn(ExperimentalMaterial3Api::class)

package com.rfm.onevsone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rfm.onevsone.domain.*
import com.rfm.onevsone.ui.*
import com.rfm.onevsone.ui.vm.ProgressViewModel

@Composable
fun MainScreen(nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.SportsEsports, null) }, label = { Text("اللعب") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.EmojiEvents, null) }, label = { Text("التحديات") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.MoreHoriz, null) }, label = { Text("المزيد") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> PlayTab(nav)
                    1 -> ChallengesTab(nav)
                    else -> MoreTab(nav)
                }
            }
        }
    }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = cs.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlayTab(nav: NavController) {
    val prefs by container().settings.flow.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(60.dp)
            Column {
                Text("أهلاً ${prefs.name} ${prefs.avatar}", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("اختر طريقة اللعب", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionCard(Icons.Filled.Groups, "إنشاء مباراة", "غرفة بكود خاص حتى 6 لاعبين دون إنترنت") { nav.navigate("create") }
        ActionCard(Icons.Filled.Link, "الانضمام لمباراة", "ادخل كود الغرفة (نفس الشبكة أو الهوتسبوت)") { nav.navigate("join") }
        ActionCard(Icons.Filled.SmartToy, "اللعب ضد الكمبيوتر", "4 مستويات صعوبة") { nav.navigate("ai_setup") }
        ActionCard(Icons.Filled.People, "اللعب المحلي", "لاعبان على نفس الجهاز") { nav.navigate("local_setup") }
        ActionCard(Icons.Filled.History, "سجل المباريات", "نتائج مبارياتك السابقة") { nav.navigate("history") }
    }
}

@Composable
fun ChallengesTab(nav: NavController) {
    val c = container()
    val vm: ProgressViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("التحديات 🏆", fontWeight = FontWeight.Black, fontSize = 24.sp)
            AssistChip(onClick = {}, label = { Text("⭐ ${ui.totalPoints} نقطة") })
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            listOf("التحديات", "الإنجازات", "الترتيب").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(Challenges.ALL) { ch ->
                    val done = ch.id in ui.completed
                    SectionCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(ch.desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text("المستوى: ${ch.level.label}") })
                                AssistChip(onClick = {}, label = { Text("+${ch.points} نقطة") })
                            }
                            Button(
                                onClick = {
                                    c.pendingGame = GameConfig(
                                        GameMode.CHALLENGE, prefs.name, prefs.avatar, "الكمبيوتر", "🤖",
                                        ch.level, if (ch.aiStarts) 2 else 1, ch
                                    )
                                    nav.navigate("game")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (done) "العب مجددًا" else "ابدأ التحدي") }
                        }
                    }
                }
            }
            1 -> LazyColumn(
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("المكافآت", fontWeight = FontWeight.Bold)
                            Text(
                                "كل إنجاز يمنحك 20 نقطة، وكل نقاطك تفتح رموزًا جديدة لصورتك الرمزية.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(Avatars.ALL.joinToString(" ") { if (ui.totalPoints >= it.second) it.first else "🔒" }, fontSize = 22.sp)
                        }
                    }
                }
                items(Progress.achievements) { a ->
                    val got = a.id in ui.unlocked
                    SectionCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (got) a.icon else "🔒", fontSize = 30.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.title, fontWeight = FontWeight.Bold)
                                Text(a.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (got) Text("+20", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> {
                if (ui.leaderboard.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد نتائج بعد. العب مباراة لتظهر في الترتيب!")
                    }
                } else LazyColumn(
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ui.leaderboard.withIndex().toList()) { (i, r) ->
                        SectionCard {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" },
                                    fontSize = 24.sp, modifier = Modifier.width(40.dp)
                                )
                                Text(r.avatar, fontSize = 28.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "فوز ${r.wins} • تعادل ${r.draws} • خسارة ${r.losses}",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("${r.points}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreTab(nav: NavController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("المزيد ⚙️", fontWeight = FontWeight.Black, fontSize = 24.sp)
        ActionCard(Icons.Filled.Settings, "إعدادات التطبيق", "المظهر، الصوت، الاهتزاز، الملف الشخصي") { nav.navigate("settings") }
        ActionCard(Icons.Filled.Share, "مشاركة التطبيق", "رابط التحميل و QR Code") { nav.navigate("share") }
        ActionCard(Icons.Filled.Star, "تقييم التطبيق", "قيّم التطبيق من 5 نجوم") { nav.navigate("rate") }
        ActionCard(Icons.Filled.Info, "حول التطبيق", "معلومات المطور والإصدار") { nav.navigate("about") }
    }
}
