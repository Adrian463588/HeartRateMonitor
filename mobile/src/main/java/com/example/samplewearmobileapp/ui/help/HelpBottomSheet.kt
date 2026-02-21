package com.example.samplewearmobileapp.ui.help

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Help & Troubleshooting BottomSheet for researchers.
 * Provides quick-reference guides and a full WebView documentation viewer.
 *
 * @param onDismiss Called when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showWebView by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        if (showWebView) {
            // === Full Documentation WebView ===
            HelpWebView(
                onBack = { showWebView = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            )
        } else {
            // === Quick Reference Guide ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Text(
                    text = "Researcher Quick Guide",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                // === Section 1: Device Setup ===
                HelpSection(
                    title = "Device Setup",
                    icon = Icons.Default.Build,
                    items = listOf(
                        HelpItem(
                            "Polar H10 Chest Strap",
                            "• Moisten the electrode pads with water or electrode gel\n" +
                            "• Position the strap just below the chest muscles\n" +
                            "• Ensure snug fit — too loose causes motion artifacts\n" +
                            "• The LED should blink once paired"
                        ),
                        HelpItem(
                            "Galaxy Watch 5 (PPG)",
                            "• Wear the watch 2cm above the wrist bone\n" +
                            "• Tighten strap so there is no gap with the skin\n" +
                            "• Avoid placing over tattoos or thick hair\n" +
                            "• Launch the wear app before starting recording"
                        )
                    )
                )

                // === Section 2: Recording Protocol ===
                HelpSection(
                    title = "Recording Protocol",
                    icon = Icons.Default.PlayCircle,
                    items = listOf(
                        HelpItem(
                            "Step-by-Step",
                            "1. Set the Polar Device ID in Settings → Device ID\n" +
                            "2. Wait for \"Connected\" status on ECG card\n" +
                            "3. Confirm PPG cards show \"Connected\" or \"Streaming\"\n" +
                            "4. Press the Record button (red pulsating FAB)\n" +
                            "5. Use the Flag button to mark events (stress, rest)\n" +
                            "6. Press Pause during breaks if needed\n" +
                            "7. Press Stop → Save All Data when finished"
                        ),
                        HelpItem(
                            "Data Directory",
                            "• Set via Menu → Set Data Directory\n" +
                            "• Default: Documents/HeartMonitor/\n" +
                            "• Each session creates timestamped CSV files"
                        )
                    )
                )

                // === Section 3: Troubleshooting ===
                HelpSection(
                    title = "Troubleshooting",
                    icon = Icons.Default.Help,
                    items = listOf(
                        HelpItem(
                            "ECG shows flat line",
                            "• Check if the Polar strap electrode pads are moist\n" +
                            "• Ensure skin contact is firm and continuous\n" +
                            "• Try Menu → Restart Polar API\n" +
                            "• If still flat, re-wear the strap with fresh moisture"
                        ),
                        HelpItem(
                            "PPG data missing",
                            "• Verify the watch app is open and running\n" +
                            "• Tap the PPG sensor card to toggle the tracker\n" +
                            "• Restart the watch app if status shows \"Disconnected\"\n" +
                            "• Ensure watch strap is tight against skin"
                        ),
                        HelpItem(
                            "App unresponsive / freezing",
                            "• Wait 5 seconds — the app may be processing a large batch\n" +
                            "• If frozen, press Stop to save current data\n" +
                            "• Use Menu → Redo Plot Setup to reset graphs"
                        ),
                        HelpItem(
                            "Recording stops unexpectedly",
                            "• Ensure battery saver is OFF on the phone\n" +
                            "• Disable battery optimization for this app\n" +
                            "• Keep the phone plugged in for long sessions"
                        )
                    )
                )

                // === Section 4: Data Files ===
                HelpSection(
                    title = "Data File Format",
                    icon = Icons.Default.Description,
                    items = listOf(
                        HelpItem(
                            "CSV Columns",
                            "• ECG: timestamp_ms, voltage_mV\n" +
                            "• PPG Green: timestamp_ms, ppg_value\n" +
                            "• PPG IR: timestamp_ms, ppg_value\n" +
                            "• PPG Red: timestamp_ms, ppg_value"
                        ),
                        HelpItem(
                            "File Naming",
                            "• Format: [type]_[YYYY-MM-DD_HHmmss].csv\n" +
                            "• Example: ECG_2026-02-21_143025.csv"
                        )
                    )
                )

                // === View Full Documentation Button ===
                OutlinedButton(
                    onClick = { showWebView = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Full Documentation")
                }
            }
        }
    }
}

// === WebView Composable ===

@Composable
private fun HelpWebView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Handle back button within WebView
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    Column(modifier = modifier) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Documentation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        HorizontalDivider()

        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    loadUrl("file:///android_asset/kedotnetecg.html")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// === Internal Components ===

private data class HelpItem(val title: String, val body: String)

@Composable
private fun HelpSection(
    title: String,
    icon: ImageVector,
    items: List<HelpItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }
            }
        }
    }
}
