@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rfm.onevsone.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rfm.onevsone.BuildConfig
import com.rfm.onevsone.domain.MatchRecord
import com.rfm.onevsone.ui.*
import com.rfm.onevsone.ui.vm.ProgressViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun modeLabel(m: String): String = when {
    m == "LOCAL" -> "محلي"
    m == "ROOM" -> "غرفة"
    m.startsWith("AI:") -> "ضد الكمبيوتر"
    m.startsWith("CH:") -> "تحدٍّ"
    else -> m
}

@Composable
fun HistoryScreen(nav: NavController) {
    val vm: ProgressViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }

    ScreenScaffold("سجل المباريات", { nav.popBackStack() }) { pad ->
        if (ui.matches.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد مباريات مسجلة بعد")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SectionCard {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatBox("لعبت", ui.stats.played)
                            StatBox("فوز", ui.stats.wins)
                            StatBox("تعادل", ui.stats.draws)
                            StatBox("خسارة", ui.stats.losses)
                        }
                    }
                }
                items(ui.matches) { m -> MatchRow(m, fmt) }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, v: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$v", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchRow(m: MatchRecord, fmt: SimpleDateFormat) {
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(modeLabel(m.mode), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.format(Date(m.ts)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${m.a1} ${m.p1}", Modifier.weight(1f), fontWeight = if (m.winner == 1) FontWeight.Black else FontWeight.Normal)
                Text("ضد", fontSize = 12.sp)
                Text("${m.a2} ${m.p2}", Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = if (m.winner == 2) FontWeight.Black else FontWeight.Normal)
            }
            val res = when {
                m.winner == 0 -> "🤝 تعادل"
                m.winner == 1 -> "🏆 فاز ${m.p1}"
                else -> "🏆 فاز ${m.p2}"
            }
            Text(res, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val c = container()
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    val pvm: ProgressViewModel = viewModel()
    val pu by pvm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { pvm.refresh() }
    var confirm by remember { mutableStateOf(false) }

    ScreenScaffold("الإعدادات", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileEditor(
                "ملفي الشخصي", prefs.name, prefs.avatar, pu.totalPoints,
                { n -> c.settings.update { it.copy(name = n) } },
                { a -> c.settings.update { it.copy(avatar = a) } }
            )
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المظهر", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "حسب النظام", 1 to "فاتح", 2 to "داكن").forEach { (v, t) ->
                            FilterChip(selected = prefs.theme == v, onClick = { c.settings.update { it.copy(theme = v) } }, label = { Text(t) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المؤثرات الصوتية", Modifier.weight(1f))
                        Switch(checked = prefs.sound, onCheckedChange = { v -> c.settings.update { it.copy(sound = v) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الاهتزاز", Modifier.weight(1f))
                        Switch(checked = prefs.vibrate, onCheckedChange = { v -> c.settings.update { it.copy(vibrate = v) } })
                    }
                }
            }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("مسح السجل والتقدم", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("مسح كل البيانات؟") },
            text = { Text("سيتم حذف سجل المباريات وتقدم التحديات والنقاط. لا يمكن التراجع.") },
            confirmButton = { TextButton(onClick = { pvm.clearAll(); confirm = false }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ShareScreen(nav: NavController) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val url = BuildConfig.DOWNLOAD_URL
    val qr = remember(url) { qrBitmap(url) }
    ScreenScaffold("مشاركة التطبيق", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LogoMark(84.dp)
            Text("امسح الرمز لتحميل 1vs1", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Image(
                bitmap = qr, contentDescription = "QR Code",
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp)
            )
            Text(url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(
                onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "جرّب لعبة XO الرائعة 1vs1 🎮\n$url")
                    }
                    ctx.startActivity(Intent.createChooser(i, "مشاركة التطبيق"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("مشاركة الرابط")
            }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("نسخ الرابط")
            }
        }
    }
}

@Composable
fun RateScreen(nav: NavController) {
    val c = container()
    val ctx = LocalContext.current
    val prefs by c.settings.flow.collectAsStateWithLifecycle()
    ScreenScaffold("تقييم التطبيق", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
        ) {
            Text("ما رأيك في 1vs1؟", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..5) {
                    IconButton(onClick = { c.settings.update { it.copy(rating = i) } }) {
                        Icon(
                            if (i <= prefs.rating) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                            tint = Color(0xFFFFC107), modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
            if (prefs.rating > 0) {
                Text(
                    when (prefs.rating) {
                        5 -> "رائع! شكرًا لدعمك 💙"
                        4 -> "شكرًا! يسعدنا أن التطبيق أعجبك"
                        else -> "شكرًا على ملاحظتك، سنعمل على التحسين"
                    },
                    textAlign = TextAlign.Center
                )
                if (prefs.rating >= 4) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DOWNLOAD_URL)))
                    }) { Text("افتح صفحة التطبيق") }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current
    val waUrl = "https://wa.me/201027814323"
    ScreenScaffold("حول التطبيق", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LogoMark(110.dp)
            Text("1vs1", fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text("الإصدار ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("لعبة XO تعمل بدون إنترنت", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المطور", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("الاسم: rFM")
                    Text("واتساب: +201027814323")
                    Button(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Call, null); Spacer(Modifier.width(8.dp)); Text("تواصل عبر واتساب")
                    }
                    Text(waUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
