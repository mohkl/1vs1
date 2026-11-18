package com.rfm.onevsone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfm.onevsone.media.Sfx
import com.rfm.onevsone.ui.LogoMark
import com.rfm.onevsone.ui.container
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private class Floater(val x: Float, val y: Float, val isX: Boolean, val phase: Float, val size: Float)

private val floaters = listOf(
    Floater(0.10f, 0.12f, true, 0.0f, 38f), Floater(0.82f, 0.10f, false, 0.2f, 34f),
    Floater(0.35f, 0.22f, false, 0.5f, 26f), Floater(0.65f, 0.28f, true, 0.7f, 30f),
    Floater(0.08f, 0.40f, false, 0.3f, 40f), Floater(0.90f, 0.46f, true, 0.9f, 36f),
    Floater(0.25f, 0.60f, true, 0.6f, 28f), Floater(0.72f, 0.66f, false, 0.1f, 42f),
    Floater(0.12f, 0.80f, true, 0.4f, 34f), Floater(0.50f, 0.86f, false, 0.8f, 30f),
    Floater(0.88f, 0.88f, true, 0.35f, 38f), Floater(0.45f, 0.06f, true, 0.15f, 24f)
)

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val c = container()
    var showWelcome by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    val inf = rememberInfiniteTransition(label = "bg")
    val drift by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "drift"
    )
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        c.sound.play(Sfx.SPLASH)
        delay(300)
        showWelcome = true
        delay(900)
        showName = true
        delay(1800)
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F1E), Color(0xFF1B1464)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (f in floaters) {
                val dy = sin((drift + f.phase) * 2.0 * PI).toFloat() * 24f
                val cx = f.x * size.width
                val cy = f.y * size.height + dy
                val r = f.size
                if (f.isX) {
                    val col = Color(0xFF22D3EE).copy(alpha = 0.22f)
                    drawLine(col, Offset(cx - r, cy - r), Offset(cx + r, cy + r), 9f, StrokeCap.Round)
                    drawLine(col, Offset(cx + r, cy - r), Offset(cx - r, cy + r), 9f, StrokeCap.Round)
                } else {
                    drawCircle(Color(0xFFF472B6).copy(alpha = 0.22f), r, Offset(cx, cy), style = Stroke(9f))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(showWelcome, enter = fadeIn(tween(600))) {
                Text(
                    "Welcome", color = Color.White.copy(alpha = 0.9f), fontSize = 28.sp,
                    fontWeight = FontWeight.Light, letterSpacing = 6.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(
                showName,
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), initialScale = 0.2f) +
                    fadeIn(tween(500))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoMark(96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF22D3EE))) { append("1") }
                            withStyle(SpanStyle(color = Color.White)) { append("vs") }
                            withStyle(SpanStyle(color = Color(0xFFF472B6))) { append("1") }
                        },
                        style = TextStyle(
                            fontSize = 72.sp, fontWeight = FontWeight.Black,
                            shadow = Shadow(cs.primary, Offset.Zero, 36f)
                        )
                    )
                }
            }
        }
    }
}
