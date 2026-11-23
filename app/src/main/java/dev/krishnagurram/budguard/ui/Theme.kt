package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFFF6F6F8)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111114)
    val Quiet = Color(0xFF5F5F66)
    val Blue = Color(0xFF1F6FEB)
    val BlueSoft = Color(0xFFE6EFFD)
    val Red = Color(0xFFD92D20)
    val RedSoft = Color(0xFFFDECEA)
    val Orange = Color(0xFFC2410C)
    val OrangeSoft = Color(0xFFFFF1E6)
    val Green = Color(0xFF12A150)
    val GreenSoft = Color(0xFFE3F6EA)
    val Grey = Color(0xFF5F5F66)
    val GreySoft = Color(0xFFEEF0F3)
    val Rule = Color(0xFFEEEEF1)
}

@Composable
fun BudGuardTheme(content: @Composable () -> Unit) {
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
        content = content,
    )
}

val blueButton @Composable get() = ButtonDefaults.buttonColors(containerColor = Palette.Blue, contentColor = Color.White)
val softButton @Composable get() = ButtonDefaults.buttonColors(containerColor = Palette.BlueSoft, contentColor = Palette.Blue)

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = Palette.Quiet)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.Quiet,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Palette.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun IconDot(icon: ImageVector, tint: Color, bg: Color, size: Int = 42) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun StatusRow(ok: Boolean?, title: String, detail: String) {
    val (tint, bg) = when (ok) {
        true -> Palette.Green to Palette.GreenSoft
        false -> Palette.Orange to Palette.OrangeSoft
        null -> Palette.Grey to Palette.GreySoft
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconDot(
            icon = if (ok == true) Icons.Filled.CheckCircle else Icons.Filled.Info,
            tint = tint,
            bg = bg,
            size = 34,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(detail, fontSize = 13.sp, color = Palette.Quiet)
        }
    }
}
