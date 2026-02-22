package com.example.samplewearmobileapp.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.samplewearmobileapp.ui.components.*
import com.example.samplewearmobileapp.ui.theme.SensorColors

/**
 * Data class holding the dashboard UI state.
 * Populated by the Activity from existing StateFlows.
 */
data class DashboardUiState(
    val polarState: SensorState = SensorState.DISCONNECTED,
    val polarDeviceName: String = "Polar H10",
    val ppgGreenState: SensorState = SensorState.DISCONNECTED,
    val ppgIrState: SensorState = SensorState.DISCONNECTED,
    val ppgRedState: SensorState = SensorState.DISCONNECTED,
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsedTime: String = "00:00",
    val ecgSampleCount: Long = 0,
    val ppgGreenSampleCount: Long = 0,
    val ppgIrSampleCount: Long = 0,
    val ppgRedSampleCount: Long = 0
)

/**
 * Main dashboard overlay composable.
 * Displays sensor status cards in a horizontal scroll row,
 * and a record control area with pulsating FAB.
 *
 * Embedded as a ComposeView within the existing XML ConstraintLayout.
 *
 * @param uiState The dashboard state
 * @param onRecord Called when record is pressed
 * @param onPause Called when pause is pressed
 * @param onResume Called when resume is pressed
 * @param onStop Called when stop is pressed
 * @param onEventMarker Called with a label when an event marker is added
 */
@Composable
fun DashboardOverlay(
    uiState: DashboardUiState,
    onRecord: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    onEventMarker: (String) -> Unit = {},
    onSensorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // === Header ===
        Text(
            text = "Sensor Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // === Sensor Status Cards Row ===
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            // Polar H10 (ECG)
            item {
                SensorStatusCard(
                    sensorName = uiState.polarDeviceName,
                    state = uiState.polarState,
                    sampleCount = uiState.ecgSampleCount,
                    icon = Icons.Default.MonitorHeart,
                    accentColor = if (isDark) SensorColors.ecgTraceDark else SensorColors.ecgTraceLight,
                    onClick = { onSensorClick("POLAR") }
                )
            }
            // PPG Green
            item {
                SensorStatusCard(
                    sensorName = "PPG Green",
                    state = uiState.ppgGreenState,
                    sampleCount = uiState.ppgGreenSampleCount,
                    icon = Icons.Default.Watch,
                    accentColor = if (isDark) SensorColors.ppgGreenTraceDark else SensorColors.ppgGreenTraceLight,
                    onClick = { onSensorClick("PPG_GREEN") }
                )
            }
            // PPG IR
            item {
                SensorStatusCard(
                    sensorName = "PPG IR",
                    state = uiState.ppgIrState,
                    sampleCount = uiState.ppgIrSampleCount,
                    icon = Icons.Default.Sensors,
                    accentColor = if (isDark) SensorColors.ppgIrTraceDark else SensorColors.ppgIrTraceLight,
                    onClick = { onSensorClick("PPG_IR") }
                )
            }
            // PPG Red
            item {
                SensorStatusCard(
                    sensorName = "PPG Red",
                    state = uiState.ppgRedState,
                    sampleCount = uiState.ppgRedSampleCount,
                    icon = Icons.Default.FavoriteBorder,
                    accentColor = if (isDark) SensorColors.ppgRedTraceDark else SensorColors.ppgRedTraceLight,
                    onClick = { onSensorClick("PPG_RED") }
                )
            }
        }

        // === Record Controls ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Event marker (left of record button)
            EventMarkerButton(
                visible = uiState.recordingState != RecordingState.IDLE,
                onMarker = onEventMarker,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Main record button
            PulsatingRecordButton(
                state = uiState.recordingState,
                elapsedTime = uiState.elapsedTime,
                onRecord = onRecord,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    val r = red * 0.2126f
    val g = green * 0.7152f
    val b = blue * 0.0722f
    return r + g + b
}
