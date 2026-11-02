package io.github.alex333333.bridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.DotMatrix

/**
 * [text] drawn as 5×7 dots. Glyphs are laid out one by one so the gap column between them stays empty instead of
 * showing unlit dots. (Copied from the lessons app: its helpers are internal to that module.)
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 4.dp,
    gap: Dp = 2.dp,
    on: Color = Color.Unspecified,
    off: Color = Color.Unspecified,
) {
    val palette = LocalPalette.current
    val lit = if (on == Color.Unspecified) palette.text else on
    val unlit = if (off == Color.Unspecified) palette.dotOff else off
    val glyphs = remember(text) { text.map { DotMatrix.layout(it.toString()) } }
    val columns = glyphs.sumOf { it.width } + (glyphs.size - 1).coerceAtLeast(0)
    if (columns <= 0) return
    Canvas(
        modifier.size(
            width = dot * columns + gap * (columns - 1),
            height = dot * DotMatrix.ROWS + gap * (DotMatrix.ROWS - 1),
        ),
    ) {
        val radius = dot.toPx() / 2
        val pitch = (dot + gap).toPx()
        var x0 = 0
        for (glyph in glyphs) {
            for (x in 0 until glyph.width) {
                for (y in 0 until glyph.height) {
                    drawCircle(
                        color = if (glyph[x, y]) lit else unlit,
                        radius = radius,
                        center = Offset((x0 + x) * pitch + radius, y * pitch + radius),
                    )
                }
            }
            x0 += glyph.width + 1
        }
    }
}

@Composable
fun Header() {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DotMatrixText(
            text = "МОСТ",
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Мост"
            },
        )
        Text("ТЕЛЕФОН · ПК", style = monoStyle(11.sp))
    }
}

/** A fully rounded chip: filled with the text color when [filled], otherwise a thin outline. */
@Composable
private fun Modifier.pill(filled: Boolean): Modifier {
    val palette = LocalPalette.current
    return clip(CircleShape).then(
        if (filled) Modifier.background(palette.text) else Modifier.border(1.dp, palette.border, CircleShape),
    )
}

@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .pill(filled = isSelected)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = monoStyle(12.sp, bold = true, color = if (isSelected) palette.background else palette.text),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * A 48dp pill with a mono caps label. [primary] is the one red button of a screen, [selected] fills it with the
 * text color (a chosen item); otherwise it is an outline.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current
    val fill = when {
        primary -> Modifier.background(palette.accent)
        selected -> Modifier.background(palette.text)
        else -> Modifier.border(1.dp, palette.border, CircleShape)
    }
    val labelColor = when {
        primary -> Color.White
        selected -> palette.background
        else -> palette.text
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(48.dp)
            .clip(CircleShape)
            .then(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = monoStyle(12.sp, bold = true, color = labelColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A rounded dark (or white) card that groups related content. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LocalPalette.current.card)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics { heading() },
        style = monoStyle(11.sp),
    )
}

/** A status dot: red when [on], grey otherwise. */
@Composable
fun StatusDot(on: Boolean) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (on) palette.accent else palette.muted),
    )
}

/** A line of small text under a button: [isError] colors it as an error. */
@Composable
fun Message(text: String, isError: Boolean = false) {
    val palette = LocalPalette.current
    Text(text, style = bodyStyle(14.sp, color = if (isError) palette.error else palette.secondary))
}

/** A one-line (or multi-line) text field with a pill outline and a [placeholder]. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val palette = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = bodyStyle(16.sp),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 6,
        cursorBrush = SolidColor(palette.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = bodyStyle(16.sp, color = palette.muted))
                innerTextField()
            }
        },
    )
}

/** Shown on the tabs that need a paired PC. */
@Composable
fun NeedPairing(onGoToPc: () -> Unit) {
    Panel {
        Text("Сначала привяжите ПК", style = bodyStyle(17.sp, medium = true))
        Text("Откройте вкладку «ПК» и введите код с экрана компьютера.", style = bodyStyle(14.sp, color = LocalPalette.current.secondary))
        PillButton("К ПРИВЯЗКЕ", onGoToPc, Modifier.fillMaxWidth())
    }
}
