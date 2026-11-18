@file:OptIn(ExperimentalMaterial3Api::class)

package com.rfm.onevsone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rfm.onevsone.domain.GameMode
import com.rfm.onevsone.ui.*
import com.rfm.onevsone.ui.vm.GameViewModel

@Composable
fun GameScreen(nav: NavController) {
    val vm: GameViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val cfg = ui.cfg
    if (cfg == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val p2Mark = if (ui.p1Mark == 'X') 'O' else 'X'
    val title = when (cfg.mode) {
        GameMode.LOCAL -> "اللعب المحلي"
        GameMode.AI -> "ضد الكمبيوتر (${cfg.level.label})"
        GameMode.CHALLENGE -> "تحدي: ${cfg.challenge?.title}"
    }
    val turnIsP1 = ui.turn == ui.p1Mark

    ScreenScaffold(title, { nav.popBackStack() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            cfg.challenge?.let {
                SectionCard {
                    Text(
                        "🎯 ${it.desc}", modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerBadge(cfg.p1Name, cfg.p1Avatar, ui.p1Mark, ui.s1, !ui.over && turnIsP1, Modifier.weight(1f))
                PlayerBadge(cfg.p2Name, cfg.p2Avatar, p2Mark, ui.s2, !ui.over && !turnIsP1, Modifier.weight(1f))
            }
            Text(
                when {
                    ui.over -> ui.resultText
                    ui.thinking -> "الكمبيوتر يفكر…"
                    else -> "الدور على ${if (turnIsP1) cfg.p1Name else cfg.p2Name}"
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            BoardView(
                board = ui.board,
                enabled = !ui.over && !ui.thinking,
                winLine = ui.winLine,
                onTap = { vm.tap(it) }
            )
            Text("تعادل: ${ui.draws}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(ui.over, enter = fadeIn() + slideInVertically { it / 2 }) {
                SectionCard {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(ui.resultText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        if (ui.challengeNote.isNotEmpty()) Text(ui.challengeNote, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.rematch() }, modifier = Modifier.weight(1f)) { Text("إعادة المباراة") }
                            OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f)) { Text("خروج") }
                        }
                    }
                }
            }
        }
    }
}
