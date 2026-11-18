@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rfm.onevsone.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.rfm.onevsone.App
import com.rfm.onevsone.AppContainer
import com.rfm.onevsone.domain.Avatars

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as App).container

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.background, c.surfaceVariant.copy(alpha = 0.55f)))),
        content = content
    )
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            content = content
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun LogoMark(dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = this.size.width
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF2A1F8F), Color(0xFF0B0F1E))),
            cornerRadius = CornerRadius(w * 0.22f)
        )
        val sw = w * 0.065f
        val a = w * 0.20f
        val b = w * 0.46f
        drawLine(Color(0xFF22D3EE), Offset(a, a), Offset(b, b), sw, StrokeCap.Round)
        drawLine(Color(0xFF22D3EE), Offset(b, a), Offset(a, b), sw, StrokeCap.Round)
        drawCircle(Color(0xFFF472B6), radius = w * 0.14f, center = Offset(w * 0.66f, w * 0.66f), style = Stroke(sw))
    }
}

@Composable
fun MarkGlyph(mark: Char, modifier: Modifier) {
    val progress by animateFloatAsState(if (mark == '.') 0f else 1f, tween(280), label = "mark")
    val cx = MaterialTheme.colorScheme.primary
    val co = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        if (progress <= 0f || mark == '.') return@Canvas
        val w = size.width
        val pad = w * 0.24f
        val sw = w * 0.11f
        val len = w - 2 * pad
        if (mark == 'X') {
            val p1 = (progress * 2f).coerceIn(0f, 1f)
            val p2 = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
            drawLine(cx, Offset(pad, pad), Offset(pad + len * p1, pad + len * p1), sw, StrokeCap.Round)
            if (p2 > 0f) {
                drawLine(cx, Offset(w - pad, pad), Offset(w - pad - len * p2, pad + len * p2), sw, StrokeCap.Round)
            }
        } else {
            drawArc(
                co, -90f, 360f * progress, false,
                topLeft = Offset(pad, pad), size = Size(len, len),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun BoardView(board: String, enabled: Boolean, winLine: List<Int>, onTap: (Int) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val cs = MaterialTheme.colorScheme
        val lineProgress by animateFloatAsState(if (winLine.size == 3) 1f else 0f, tween(450), label = "win")
        Box(Modifier.fillMaxWidth().widthIn(max = 380.dp).aspectRatio(1f)) {
            Column(Modifier.matchParentSize()) {
                for (r in 0..2) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (cc in 0..2) {
                            val i = r * 3 + cc
                            val hl = i in winLine
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (hl) cs.primaryContainer else cs.surfaceVariant)
                                    .clickable(enabled = enabled && board[i] == '.') { onTap(i) }
                            ) { MarkGlyph(board[i], Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                if (winLine.size == 3 && lineProgress > 0f) {
                    val cell = size.width / 3f
                    fun c(i: Int) = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                    val a = c(winLine[0])
                    val b = c(winLine[2])
                    drawLine(
                        Color(0xFFFFD54F), a,
                        Offset(a.x + (b.x - a.x) * lineProgress, a.y + (b.y - a.y) * lineProgress),
                        cell * 0.09f, StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerBadge(name: String, avatar: String, mark: Char, score: Int?, active: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val mc = if (mark == 'X') cs.primary else cs.secondary
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) cs.primaryContainer else cs.surface),
        border = if (active) BorderStroke(2.dp, mc) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(avatar, fontSize = 34.sp)
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mark.toString(), color = mc, fontWeight = FontWeight.Black, fontSize = 22.sp)
                if (score != null) {
                    Spacer(Modifier.width(10.dp))
                    Text("$score", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AvatarPicker(selected: String, points: Int, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Avatars.ALL) { (emoji, need) ->
                val locked = points < need
                val sel = emoji == selected
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (sel) cs.primaryContainer else cs.surfaceVariant)
                        .then(if (sel) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                        .clickable(enabled = !locked) { onSelect(emoji) },
                    contentAlignment = Alignment.Center
                ) { Text(if (locked) "🔒" else emoji, fontSize = 26.sp) }
            }
        }
        val next = Avatars.ALL.map { it.second }.filter { it > points }.minOrNull()
        if (next != null) {
            Text("رمز جديد يُفتح عند $next نقطة (نقاطك: $points)", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileEditor(
    title: String, name: String, avatar: String, points: Int,
    onName: (String) -> Unit, onAvatar: (String) -> Unit
) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(
                value = name, onValueChange = { onName(it.take(14)) },
                label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            AvatarPicker(avatar, points, onAvatar)
        }
    }
}

fun qrBitmap(text: String, size: Int = 640): androidx.compose.ui.graphics.ImageBitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1)
    )
    val px = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        px[y * size + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}
