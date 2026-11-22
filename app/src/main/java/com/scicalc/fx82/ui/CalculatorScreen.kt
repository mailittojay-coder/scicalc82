package com.scicalc.fx82.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.scicalc.fx82.CalcViewModel
import com.scicalc.fx82.core.AngleUnit
import com.scicalc.fx82.core.DisplayMode
import com.scicalc.fx82.core.Key
import com.scicalc.fx82.core.KeyStyle
import com.scicalc.fx82.core.Layout
import com.scicalc.fx82.core.UiState

@Composable
fun CalculatorScreen(vm: CalcViewModel) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Body)
                .systemBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Display(vm.ui, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(8.dp))
                Keypad(vm.ui, vm::press, Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (vm.showModeMenu) ModeDialog(vm)
    }
}

// ───────────────────────────── LCD ─────────────────────────────

@Composable
private fun Display(ui: UiState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.LcdBg, Color(0xFFB5C1A4))))
            .border(2.dp, Palette.BodyEdge, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            StatusRow(ui)
            ExpressionLine(ui, Modifier.weight(0.42f).fillMaxWidth())
            ResultArea(ui, Modifier.weight(0.58f).fillMaxWidth())
        }
    }
}

@Composable
private fun StatusRow(ui: UiState) {
    Row(
        Modifier.fillMaxWidth().height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        @Composable
        fun flag(text: String, on: Boolean) {
            if (on) Text(text, color = Palette.LcdInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        flag("S", ui.shift)
        flag("A", ui.alpha)
        flag("M", ui.hasMemory)
        flag("STO", ui.store)
        flag("RCL", ui.recall)
        flag("hyp", ui.hyp)
        flag("OVR", ui.overwrite)
        Spacer(Modifier.weight(1f))
        flag(
            when (ui.angle) { AngleUnit.DEG -> "DEG"; AngleUnit.RAD -> "RAD"; AngleUnit.GRA -> "GRA" },
            true
        )
        flag(ui.displayLabel, ui.displayLabel.isNotEmpty())
        flag("▲", ui.canScrollUp)
        flag("▼", ui.canScrollDown)
    }
}

@Composable
private fun ExpressionLine(ui: UiState, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.before, ui.after) {
        withFrameNanos { }
        if (ui.after.isEmpty()) scroll.scrollTo(scroll.maxValue)
    }
    val editing = ui.resultLines.isEmpty()
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f, targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            ExprText(ui.before)
            if (editing) {
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .width(if (ui.overwrite) 12.dp else 2.dp)
                        .height(26.dp)
                        .alpha(if (ui.overwrite) 0.35f else cursorAlpha)
                        .background(Palette.LcdInk)
                )
            }
            ExprText(ui.after)
        }
    }
}

@Composable
private fun ExprText(text: String) {
    if (text.isEmpty()) return
    Text(
        text,
        color = Palette.LcdInk,
        fontSize = 26.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun ResultArea(ui: UiState, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        val error = ui.error
        when {
            error != null -> Text(
                error, color = Palette.LcdInk, fontSize = 26.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            )
            ui.resultLines.isNotEmpty() -> Column(horizontalAlignment = Alignment.End) {
                val multi = ui.resultLines.size > 1
                ui.resultLines.forEach { line ->
                    Text(
                        line,
                        color = Palette.LcdInk,
                        fontSize = when {
                            multi -> 22.sp
                            line.length <= 11 -> 40.sp
                            line.length <= 14 -> 32.sp
                            else -> 26.sp
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End
                    )
                }
            }
            ui.before.isEmpty() && ui.after.isEmpty() -> Text(
                "0", color = Palette.LcdInk, fontSize = 40.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
            )
        }
    }
}

// ───────────────────────────── keypad ─────────────────────────────

private val rowWeights = listOf(0.75f, 0.85f, 0.85f, 0.85f, 0.85f, 1.1f, 1.1f, 1.1f, 1.1f)

@Composable
private fun Keypad(ui: UiState, onPress: (Key) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Layout.rows.forEachIndexed { i, row ->
            Row(Modifier.weight(rowWeights[i]).fillMaxWidth()) {
                row.forEach { key ->
                    KeyCell(key, ui, onPress, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun KeyCell(key: Key, ui: UiState, onPress: (Key) -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    val alphaOn = ui.alpha || ui.store || ui.recall
    val modifierActive =
        (key.style == KeyStyle.SHIFT && ui.shift) || (key.style == KeyStyle.ALPHA && ui.alpha)

    Column(modifier.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().height(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Legend(key.shift?.label, Palette.ShiftGold, ui.shift)
            Legend(key.alpha?.label, Palette.AlphaRed, alphaOn)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(Palette.keyColor(key.style))
                .then(
                    if (modifierActive) Modifier.border(2.dp, Color.White, shape) else Modifier
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPress(key)
                },
            contentAlignment = Alignment.Center
        ) {
            val label = key.primary.label
            Text(
                label,
                color = Palette.keyText(key.style),
                fontSize = when {
                    key.style == KeyStyle.DIGIT -> 22.sp
                    key.style == KeyStyle.OPERATOR || key.style == KeyStyle.EQUALS -> 22.sp
                    label.length <= 2 -> 17.sp
                    label.length == 3 -> 15.sp
                    label.length <= 5 -> 13.sp
                    else -> 11.sp
                },
                fontWeight = if (key.style == KeyStyle.DIGIT) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun Legend(text: String?, color: Color, active: Boolean) {
    if (text == null) {
        Spacer(Modifier.width(1.dp))
        return
    }
    Text(
        text,
        color = if (active) Palette.KeyDigitText else color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .then(if (active) Modifier.background(color) else Modifier)
            .padding(horizontal = 2.dp)
    )
}

// ───────────────────────────── MODE menu ─────────────────────────────

@Composable
private fun ModeDialog(vm: CalcViewModel) {
    var angle by remember { mutableStateOf(vm.angle) }
    var display by remember { mutableStateOf(vm.displayMode) }
    var mixed by remember { mutableStateOf(vm.mixedFractions) }

    AlertDialog(
        onDismissRequest = vm::dismissModeMenu,
        confirmButton = { TextButton(onClick = vm::dismissModeMenu) { Text("Done") } },
        title = { Text("Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("Angle unit")
                ChipRow(
                    listOf("Deg", "Rad", "Gra"),
                    AngleUnit.entries.indexOf(angle)
                ) { i ->
                    angle = AngleUnit.entries[i]; vm.setAngle(angle)
                }

                Section("Display")
                val kind = when (display) {
                    is DisplayMode.Norm1 -> 0
                    is DisplayMode.Norm2 -> 1
                    is DisplayMode.Fix -> 2
                    is DisplayMode.Sci -> 3
                }
                ChipRow(listOf("Norm 1", "Norm 2", "Fix", "Sci"), kind) { i ->
                    display = when (i) {
                        0 -> DisplayMode.Norm1
                        1 -> DisplayMode.Norm2
                        2 -> DisplayMode.Fix(2)
                        else -> DisplayMode.Sci(3)
                    }
                    vm.setDisplay(display)
                }

                when (val d = display) {
                    is DisplayMode.Fix -> {
                        Section("Decimal places")
                        DigitChips(0..9, d.digits) { display = DisplayMode.Fix(it); vm.setDisplay(display) }
                    }
                    is DisplayMode.Sci -> {
                        Section("Significant digits")
                        DigitChips(1..10, d.sig) { display = DisplayMode.Sci(it); vm.setDisplay(display) }
                    }
                    else -> {}
                }

                Section("Fraction result")
                ChipRow(listOf("a b/c", "d/c"), if (mixed) 0 else 1) { i ->
                    mixed = i == 0; vm.setMixed(mixed)
                }
            }
        }
    )
}

@Composable
private fun Section(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.ShiftGold)
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false) }
            )
        }
    }
}

@Composable
private fun DigitChips(range: IntRange, selected: Int, onSelect: (Int) -> Unit) {
    val all = range.toList()
    val half = (all.size + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(all.take(half), all.drop(half)).forEach { rowDigits ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowDigits.forEach { n ->
                    FilterChip(
                        selected = n == selected,
                        onClick = { onSelect(n) },
                        label = { Text("$n", fontSize = 13.sp) }
                    )
                }
            }
        }
    }
}
