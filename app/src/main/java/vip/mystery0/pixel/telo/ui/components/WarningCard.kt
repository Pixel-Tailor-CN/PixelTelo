package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WarningCard(
    containerColor: Color,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    message: String,
    inlineActionWhenPossible: Boolean = false,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        val inline = inlineActionWhenPossible &&
            maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            if (inline) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    WarningIcon(icon, iconColor)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        WarningText(title, message, containerColor)
                    }
                    content()
                }
            } else {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    WarningIcon(icon, iconColor)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        WarningText(title, message, containerColor)
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningIcon(icon: ImageVector, iconColor: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.padding(top = 2.dp).size(24.dp),
    )
}

@Composable
private fun WarningText(title: String, message: String, containerColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColorFor(containerColor),
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = contentColorFor(containerColor),
    )
}
