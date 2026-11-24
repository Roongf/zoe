package com.qianduoduo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class QianThemeTest {
    @Test fun lightThemeKeepsTextAndAmountsLegible() = checkTheme(dark = false)
    @Test fun darkThemeKeepsTextAndAmountsLegible() = checkTheme(dark = true)

    private fun checkTheme(dark: Boolean) {
        val s = qianColorScheme(dark)
        val money = qianMoneyColors(dark)
        readable("screen text", s.onSurface, s.surface)
        readable("supporting text", s.onSurfaceVariant, s.surface)
        readable("card captions", s.onSurfaceVariant, s.surfaceContainerLow)
        readable("primary button", s.onPrimary, s.primary)
        readable("primary action text", s.primary, s.surface)
        readable("summary + FAB", s.onPrimaryContainer, s.primaryContainer)
        readable("selected chart month", s.onSecondaryContainer, s.secondaryContainer)
        readable("tertiary container", s.onTertiaryContainer, s.tertiaryContainer)
        readable("error text", s.error, s.surface)
        for ((name, color) in listOf("expense" to money.expense, "income" to money.income)) {
            readable("$name on the summary", color, s.primaryContainer)
            readable("$name on a day header", color, s.surfaceContainer)
            readable("$name on the ledger", color, s.surface)
            readable("$name in the chart legend", color, s.surfaceContainerLow)
            readable("$name bar on the selected month", color, s.secondaryContainer, minimum = 3f)
        }
        readable("transfer", money.transfer, s.surface)
        readable("category ranking bar", s.primary, s.surfaceContainerHighest, minimum = 3f)
        readable("input outline", s.outline, s.surface, minimum = 3f)
    }

    private fun readable(label: String, foreground: Color, background: Color, minimum: Float = 4.5f) {
        val brighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val contrast = (brighter + 0.05f) / (darker + 0.05f)
        assertTrue(contrast >= minimum, "$label contrast $contrast is below $minimum:1")
    }
}
