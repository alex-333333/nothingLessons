package io.github.alex333333.lessons.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alex333333.lessons.core.Config
import io.github.alex333333.lessons.core.FontChoice
import io.github.alex333333.lessons.core.FontWeightChoice

private val FontChoice.caption: String
    get() = when (this) {
        FontChoice.NotoSerif -> "NOTO SERIF · ЛЁГКИЕ ЗАСЕЧКИ"
        FontChoice.RobotoSerif -> "ROBOTO SERIF · УЗКИЙ, КАК NTYPE"
        FontChoice.Inter -> "INTER · БЕЗ ЗАСЕЧЕК"
    }

private val FontWeightChoice.label: String
    get() = when (this) {
        FontWeightChoice.Regular -> "Обычное"
        FontWeightChoice.Medium -> "Среднее"
        FontWeightChoice.Bold -> "Жирное"
    }

@Composable
internal fun LookTab(config: Config, onUpdate: ConfigUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("ШРИФТ")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (font in FontChoice.entries) {
                    FontCard(font, config.weight, selected = font == config.font) {
                        onUpdate { it.copy(font = font) }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("НАЧЕРТАНИЕ")
            WeightSelector(config.font, config.weight) { weight -> onUpdate { it.copy(weight = weight) } }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Светлая или тёмная тема виджета берётся из настроек телефона.",
                style = bodyStyle(13.sp, color = Palette.Secondary).copy(lineHeight = 19.sp),
            )
            Text(
                "Шрифты Noto Serif, Roboto Serif, Inter и Martian Mono — SIL Open Font License 1.1",
                style = bodyStyle(11.sp, color = Palette.Secondary).copy(lineHeight = 15.sp),
            )
        }
    }
}

@Composable
private fun FontCard(font: FontChoice, weight: FontWeightChoice, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, if (selected) Palette.Text else Palette.Divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Физика 12:05",
                style = subjectStyle(rememberSubjectFont(font, weight), 30.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(font.caption, style = monoStyle(11.sp, spacing = 0.06f), maxLines = 1)
        }
        RadioRing(selected)
    }
}

@Composable
private fun RadioRing(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(1.5.dp, if (selected) Palette.Text else Palette.Muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent),
            )
        }
    }
}

@Composable
private fun WeightSelector(font: FontChoice, selected: FontWeightChoice, onSelect: (FontWeightChoice) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, Palette.Border, CircleShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (weight in FontWeightChoice.entries) {
            val isSelected = weight == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.Text else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(weight) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = weight.label,
                    style = subjectStyle(
                        rememberSubjectFont(font, weight),
                        15.sp,
                        color = if (isSelected) Palette.Background else Palette.Text,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun LookTabPreview() {
    SettingsTheme {
        Box(Modifier.background(Color.Black).padding(16.dp)) {
            LookTab(Config(font = FontChoice.RobotoSerif), onUpdate = {})
        }
    }
}
