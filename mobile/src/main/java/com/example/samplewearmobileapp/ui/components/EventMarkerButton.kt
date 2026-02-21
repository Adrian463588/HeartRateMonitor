package com.example.samplewearmobileapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Small FAB for placing event markers during recording sessions.
 * Researchers use this to annotate stress onset, rest periods, etc.
 *
 * @param visible Whether the button is visible (only during recording)
 * @param onMarker Called with the label when the researcher adds a marker
 */
@Composable
fun EventMarkerButton(
    visible: Boolean,
    onMarker: (label: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showDialog by remember { mutableStateOf(false) }
    var markerLabel by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showDialog = true
            },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = "Add Event Marker",
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Event marker dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    "Add Event Marker",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Mark this point in the recording with a label:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = markerLabel,
                        onValueChange = { markerLabel = it },
                        label = { Text("Label") },
                        placeholder = { Text("e.g. Stress onset") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Quick presets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Stress", "Rest", "Task Start", "Task End").forEach { preset ->
                            SuggestionChip(
                                onClick = { markerLabel = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarker(markerLabel.ifBlank { "Marker" })
                        markerLabel = ""
                        showDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    markerLabel = ""
                    showDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}
