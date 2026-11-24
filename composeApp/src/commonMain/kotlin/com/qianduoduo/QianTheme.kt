package com.qianduoduo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Material Color Utilities: Tonal Spot, seed #445BC5, standard contrast.
// Store the resolved roles so the brand palette is consistent offline and on Android and desktop.
private val daylight = lightColorScheme(
    primary = Color(0xFF505B92), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF), onPrimaryContainer = Color(0xFF384379),
    inversePrimary = Color(0xFFB9C3FF),
    secondary = Color(0xFF5A5D72), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE1F9), onSecondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFF76546E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F2), onTertiaryContainer = Color(0xFF5C3C55),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF505B92),
    surfaceDim = Color(0xFFDBD9E0), surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF767680), outlineVariant = Color(0xFFC6C5D0),
    inverseSurface = Color(0xFF303036), inverseOnSurface = Color(0xFFF2F0F7),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
)
private val night = darkColorScheme(
    primary = Color(0xFFB9C3FF), onPrimary = Color(0xFF212C61),
    primaryContainer = Color(0xFF384379), onPrimaryContainer = Color(0xFFDEE1FF),
    inversePrimary = Color(0xFF505B92),
    secondary = Color(0xFFC3C5DD), onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434659), onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFE5BAD8), onTertiary = Color(0xFF44263E),
    tertiaryContainer = Color(0xFF5C3C55), onTertiaryContainer = Color(0xFFFFD7F2),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceTint = Color(0xFFB9C3FF),
    surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E1E9), inverseOnSurface = Color(0xFF303036),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)
internal data class MoneyColors(val expense: Color, val income: Color, val transfer: Color) {
    fun forType(type: TxnType) = when (type) {
        TxnType.EXPENSE -> expense
        TxnType.INCOME -> income
        TxnType.TRANSFER -> transfer
    }
}
// Accounting colors are intentionally separate from Material's error and action colors.
private val dayMoney = MoneyColors(Color(0xFF952C3A), Color(0xFF005E4B), daylight.primary)
private val nightMoney = MoneyColors(Color(0xFFFFB3B6), Color(0xFF74D9BA), night.primary)
internal fun qianColorScheme(dark: Boolean) = if (dark) night else daylight
internal fun qianMoneyColors(dark: Boolean) = if (dark) nightMoney else dayMoney
internal val LocalMoneyColors = compositionLocalOf { dayMoney }

internal val WalletIcon by lazy {
    ImageVector.Builder("Wallet", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 17f)
            horizontalLineTo(22f)
            verticalLineTo(19f)
            quadTo(22f, 21f, 20f, 21f)
            horizontalLineTo(4f)
            quadTo(2f, 21f, 2f, 19f)
            verticalLineTo(5f)
            quadTo(2f, 3f, 4f, 3f)
            horizontalLineTo(20f)
            quadTo(22f, 3f, 22f, 5f)
            verticalLineTo(7f)
            horizontalLineTo(20f)
            lineTo(20f, 6f)
            horizontalLineTo(4f)
            verticalLineTo(18f)
            horizontalLineTo(20f)
            close()
        }
        path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
            moveTo(15f, 8f)
            horizontalLineTo(22f)
            quadTo(23f, 8f, 23f, 9f)
            verticalLineTo(15f)
            quadTo(23f, 16f, 22f, 16f)
            horizontalLineTo(15f)
            quadTo(12f, 16f, 12f, 13f)
            verticalLineTo(11f)
            quadTo(12f, 8f, 15f, 8f)
            close()
            moveTo(17.5f, 12f)
            quadTo(17.5f, 10.5f, 16f, 10.5f)
            quadTo(14.5f, 10.5f, 14.5f, 12f)
            quadTo(14.5f, 13.5f, 16f, 13.5f)
            quadTo(17.5f, 13.5f, 17.5f, 12f)
            close()
        }
    }.build()
}

@Composable
internal fun QianTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoneyColors provides qianMoneyColors(dark)) {
        MaterialTheme(colorScheme = qianColorScheme(dark), content = content)
    }
}

private val emoji = mapOf(
    "restaurant" to "🍽️", "breakfast" to "🥣", "lunch" to "🍚", "dinner" to "🍜",
    "groceries" to "🥬", "takeout" to "🥡", "snack" to "🧋",
    "transport" to "🚌", "transit" to "🚇", "taxi" to "🚕", "fuel" to "⛽", "parking" to "🅿️",
    "shopping" to "🛍️", "daily" to "🧻", "clothing" to "👕", "digital" to "📱",
    "home" to "🏠", "rent" to "🔑", "utilities" to "💡", "property" to "🧹",
    "entertainment" to "🎮", "movie" to "🎬", "game" to "🕹️", "travel" to "✈️",
    "medical" to "🏥", "doctor" to "🩺", "medicine" to "💊", "fitness" to "🏃",
    "education" to "🎓", "book" to "📚", "course" to "✏️", "phone" to "📞",
    "gift" to "🎁", "more" to "📦", "salary" to "💰", "bonus" to "🏆",
    "investment" to "📈", "redpacket" to "🧧", "wallet" to "👛", "wechat" to "💬", "alipay" to "🐜",
)
@Composable
internal fun CategoryIcon(icon: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, selected: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val tint = when (icon) {
        "restaurant", "breakfast", "lunch", "dinner", "groceries", "takeout", "snack",
        "entertainment", "movie", "game", "gift", "redpacket" -> scheme.tertiaryContainer
        "transport", "transit", "taxi", "fuel", "parking", "home", "rent", "utilities",
        "property", "wallet", "wechat", "alipay" -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val selection = if (selected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier
    Box(
        modifier.size(size).background(if (selected) scheme.primaryContainer else tint, CircleShape).then(selection),
        contentAlignment = Alignment.Center,
    ) { Text(emoji[icon] ?: "📌", fontSize = if (size >= 44.dp) 22.sp else 19.sp) }
}
