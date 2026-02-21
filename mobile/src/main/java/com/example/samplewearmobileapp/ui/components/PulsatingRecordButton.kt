package com.example.samplewearmobileapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.samplewearmobileapp.ui.theme.SensorColors

/**
 * Recording state for the FAB.
 */
enum class RecordingState {
    IDLE, RECORDING, PAUSED
}

/**
 * A pulsating Record/Pause/Resume FAB with haptic feedback.
 *
 * - IDLE: Grey circle with play icon
 * - RECORDING: Pulsating red/glow with record icon
 * - PAUSED: Amber circle with play icon (static)
 *
 * @param state Current recording state
 * @param elapsedTime Formatted elapsed time string (e.g., "01:23")
 * @param onRecord Called when user presses record from IDLE
 * @param onPause Called when user presses pause from RECORDING
 * @param onResume Called when user presses resume from PAUSED
 * @param onStop Called when user long-presses the stop button
 */
@Composable
fun PulsatingRecordButton(
    state: RecordingState,
    elapsedTime: String = "00:00",
    onRecord: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }

    // Pulsating animation (only when recording)
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonScale = if (state == RecordingState.RECORDING) pulseScale else 1f

    val containerColor = when (state) {
        RecordingState.IDLE -> SensorColors.idleGrey
        RecordingState.RECORDING -> if (isDark) SensorColors.recordingPulseDark else SensorColors.recordingPulse
        RecordingState.PAUSED -> SensorColors.pausedAmber
    }

    val icon = when (state) {
        RecordingState.IDLE -> Icons.Default.PlayArrow
        RecordingState.RECORDING -> Icons.Default.Pause
        RecordingState.PAUSED -> Icons.Default.PlayArrow
    }

    val contentDescription = when (state) {
        RecordingState.IDLE -> "Start Recording"
        RecordingState.RECORDING -> "Pause Recording"
        RecordingState.PAUSED -> "Resume Recording"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main Record FAB
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                when (state) {
                    RecordingState.IDLE -> onRecord()
                    RecordingState.RECORDING -> onPause()
                    RecordingState.PAUSED -> onResume()
                }
            },
            modifier = Modifier
                .size(64.dp)
                .scale(buttonScale)
                .let {
                    if (state == RecordingState.RECORDING) {
                        it.shadow(8.dp, CircleShape, ambientColor = containerColor, spotColor = containerColor)
                    } else it
                },
            containerColor = containerColor,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp)
            )
        }

        // Elapsed time label (visible when recording or paused)
        if (state != RecordingState.IDLE) {
            Text(
                text = elapsedTime,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Stop button (visible when recording or paused)
        if (state != RecordingState.IDLE) {
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStop()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Stop", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}
