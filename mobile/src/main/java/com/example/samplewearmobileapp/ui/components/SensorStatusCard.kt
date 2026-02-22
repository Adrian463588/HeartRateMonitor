package com.example.samplewearmobileapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samplewearmobileapp.ui.theme.SensorColors

/**
 * Connection state for a sensor card.
 */
enum class SensorState {
    DISCONNECTED, CONNECTING, CONNECTED, STREAMING, MEASURING, PAUSED, STOPPED
}

/**
 * A Material 3 card showing the status of a single sensor.
 * Uses semantic colors and animated state transitions.
 *
 * @param sensorName Display name (e.g., "Polar H10", "PPG Green")
 * @param state Current connection/streaming state
 * @param sampleCount Number of samples collected (animated counter)
 * @param icon Icon representing the sensor
 * @param accentColor Trace color for this sensor
 * @param modifier Compose modifier
 */
@Composable
fun SensorStatusCard(
    sensorName: String,
    state: SensorState,
    sampleCount: Long = 0,
    icon: ImageVector = Icons.Default.Sensors,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }

    // Animated indicator color
    val indicatorColor by animateColorAsState(
        targetValue = when (state) {
            SensorState.DISCONNECTED -> MaterialTheme.colorScheme.error
            SensorState.CONNECTING -> if (isDark) SensorColors.connectingDark else SensorColors.connectingLight
            SensorState.CONNECTED -> if (isDark) SensorColors.activeDark else SensorColors.activeLight
            SensorState.STREAMING -> if (isDark) SensorColors.activeDark else SensorColors.activeLight
            SensorState.MEASURING -> if (isDark) SensorColors.activeDark else SensorColors.activeLight
            SensorState.PAUSED -> SensorColors.pausedAmber
            SensorState.STOPPED -> SensorColors.idleGrey
        },
        animationSpec = tween(durationMillis = 400),
        label = "indicatorColor"
    )

    // Animated border alpha for streaming emphasis
    val borderAlpha by animateFloatAsState(
        targetValue = if (state == SensorState.STREAMING || state == SensorState.MEASURING) 1f else 0.3f,
        animationSpec = tween(durationMillis = 300),
        label = "borderAlpha"
    )

    val stateIcon = when (state) {
        SensorState.DISCONNECTED -> Icons.Default.BluetoothDisabled
        SensorState.CONNECTING -> Icons.Default.Bluetooth
        SensorState.CONNECTED -> Icons.Default.BluetoothConnected
        SensorState.STREAMING -> Icons.Default.BluetoothConnected
        SensorState.MEASURING -> Icons.Default.BluetoothConnected
        SensorState.PAUSED -> Icons.Default.Bluetooth
        SensorState.STOPPED -> Icons.Default.Sensors
    }

    val statusText = when (state) {
        SensorState.DISCONNECTED -> "Disconnected"
        SensorState.CONNECTING -> "Connecting…"
        SensorState.CONNECTED -> "Connected"
        SensorState.STREAMING -> "Streaming"
        SensorState.MEASURING -> "Measuring"
        SensorState.PAUSED -> "Paused"
        SensorState.STOPPED -> "Stopped"
    }

    Card(
        modifier = modifier
            .width(152.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Colored left accent border
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top row: icon + name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = sensorName,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = sensorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Status row: indicator dot + state text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Icon(
                    imageVector = stateIcon,
                    contentDescription = statusText,
                    tint = indicatorColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sample count (shown when streaming or connected with data)
            if (sampleCount > 0) {
                Text(
                    text = "$sampleCount samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Utility extension to get luminance from a Color */
private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}
