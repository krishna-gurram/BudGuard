package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.R

object Palette {
    val Bg = Color(0xFFF3F4F6)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111114)
    val Quiet = Color(0xFF6B6E76)
    val Faint = Color(0xFF9A9DA5)
    val Blue = Color(0xFF1F6FEB)
    val BlueSoft = Color(0xFFE6EFFD)
    val Red = Color(0xFFD92D20)
    val RedSoft = Color(0xFFFDECEA)
    val Orange = Color(0xFFC2410C)
    val OrangeSoft = Color(0xFFFFF1E6)
    val Green = Color(0xFF12A150)
    val GreenSoft = Color(0xFFE3F6EA)
    val Purple = Color(0xFF7A3FD1)
    val PurpleSoft = Color(0xFFF1EAFB)
    val Grey = Color(0xFF6B6E76)
    val GreySoft = Color(0xFFEEF0F3)
    val Rule = Color(0xFFEDEEF1)
    val Map = Color(0xFFE7EAEE)
}

/** Body text everywhere. */
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
    Font(R.font.inter_800, FontWeight.ExtraBold),
)

/** Titles and big numbers. */
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

object Type {
    val Display = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.5).sp, color = Palette.Ink)
    val Title = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.3).sp, color = Palette.Ink)
    val Headline = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
    val Row = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Ink)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, color = Palette.Ink)
    val Sub = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.Quiet)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Palette.Quiet)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Palette.Ink)
}

@Composable
fun BudGuardTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Blue,
            onPrimary = Color.White,
            primaryContainer = Palette.BlueSoft,
            background = Palette.Bg,
            surface = Palette.Card,
            onSurface = Palette.Ink,
            onSurfaceVariant = Palette.Quiet,
            error = Palette.Red,
        ),
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = Manrope),
            displayMedium = base.displayMedium.copy(fontFamily = Manrope),
            displaySmall = base.displaySmall.copy(fontFamily = Manrope),
            headlineLarge = base.headlineLarge.copy(fontFamily = Manrope),
            headlineMedium = base.headlineMedium.copy(fontFamily = Manrope),
            headlineSmall = base.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.inter(),
            titleSmall = base.titleSmall.inter(),
            bodyLarge = base.bodyLarge.inter(),
            bodyMedium = base.bodyMedium.inter(),
            bodySmall = base.bodySmall.inter(),
            labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.inter(),
            labelSmall = base.labelSmall.inter(),
        ),
        content = content,
    )
}

val blueButton @Composable get() = ButtonDefaults.buttonColors(containerColor = Palette.Blue, contentColor = Color.White)
val softButton @Composable get() = ButtonDefaults.buttonColors(containerColor = Palette.BlueSoft, contentColor = Palette.Blue)

// ---------- Building blocks, One UI style ----------

/** Big centred title, One UI style, with room above it. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = Type.Display, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = Type.Sub.copy(fontSize = 14.sp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = Type.Label, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
}

/** A white rounded card with padding. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A white rounded card holding list rows edge to edge. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun RowDivider(inset: Dp = 70.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset, end = 18.dp).height(1.dp).background(Palette.Rule))
}

/** Rounded-square icon with a solid colour, like One UI settings. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Round icon on a soft tint. */
@Composable
fun IconDot(icon: ImageVector, tint: Color, bg: Color, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.52f).dp))
    }
}

/** One list row: leading icon, title and detail, optional trailing content. */
@Composable
fun ListRow(
    title: String,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    detailColor: Color = Palette.Quiet,
    extra: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = Type.Sub.copy(color = detailColor, fontWeight = if (detailColor != Palette.Quiet) FontWeight.SemiBold else FontWeight.Normal),
                )
            }
            if (extra != null) Text(extra, style = Type.Sub.copy(fontSize = 12.sp, color = Palette.Faint))
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun Chevron() {
    Icon(Sym.ChevronRight, contentDescription = null, tint = Palette.Faint, modifier = Modifier.size(22.dp))
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Palette.Blue,
            checkedThumbColor = Color.White,
            checkedBorderColor = Palette.Blue,
            uncheckedTrackColor = Color(0xFFDADCE1),
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color(0xFFDADCE1),
        ),
    )
}

/** Square action tile with an icon over a label (Directions, Play sound…). */
@Composable
fun RowScope.ActionTile(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val filled = primary || active
    val bg = when {
        !enabled -> Palette.GreySoft
        filled -> Palette.Blue
        else -> Palette.Card
    }
    val fg = when {
        !enabled -> Palette.Faint
        filled -> Color.White
        else -> Palette.Blue
    }
    Column(
        Modifier.weight(1f).height(86.dp).clip(RoundedCornerShape(22.dp)).background(bg).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = Type.Small.copy(color = if (filled && enabled) Color.White else if (enabled) Palette.Ink else Palette.Faint), maxLines = 1)
    }
}

enum class PillStyle { Primary, Soft, Neutral, Danger }

/** Rounded text button. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, style: PillStyle = PillStyle.Primary, enabled: Boolean = true, onClick: () -> Unit) {
    val (bg, fg) = when (style) {
        PillStyle.Primary -> Palette.Blue to Color.White
        PillStyle.Soft -> Palette.BlueSoft to Palette.Blue
        PillStyle.Neutral -> Palette.GreySoft to Palette.Ink
        PillStyle.Danger -> Palette.RedSoft to Palette.Red
    }
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(50)).background(if (enabled) bg else Palette.GreySoft)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Row.copy(fontSize = 15.sp, color = if (enabled) fg else Palette.Faint), maxLines = 1)
    }
}

/** Small selectable chip (filters, icon choice). */
@Composable
fun Chip(text: String, selected: Boolean, idle: Color = Palette.Card, onClick: () -> Unit) {
    Text(
        text,
        style = Type.Small.copy(fontSize = 13.sp, color = if (selected) Color.White else Palette.Ink),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Palette.Ink else idle)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text,
        style = Type.Small.copy(color = fg),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Coloured badge floating over a map: "Left behind", "With you". */
@Composable
fun MapBadge(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(color).padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Small.copy(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold))
    }
}

/** Round white button floating over a map. */
@Composable
fun MapButton(icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier, tint: Color = Palette.Ink, onClick: () -> Unit) {
    Box(
        modifier.size(46.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** The grabber at the top of a bottom sheet. */
@Composable
fun ColumnScope.SheetHandle() {
    Box(
        Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 5.dp)
            .clip(RoundedCornerShape(50)).background(Color(0xFFD5D7DC)),
    )
}

@Composable
fun StatusRow(ok: Boolean?, title: String, detail: String) {
    val (tint, icon) = when (ok) {
        true -> Palette.Green to Sym.Check
        false -> Palette.Orange to Sym.Warning
        null -> Palette.Grey to Sym.Info
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconTile(icon, tint, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Row.copy(fontSize = 15.sp))
            Text(detail, style = Type.Sub)
        }
    }
}
