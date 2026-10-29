package com.nuvio.app.features.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun AddonDiagnosticsPanel(modifier: Modifier = Modifier) {
    val entries by AddonDiagnostics.entries.collectAsStateWithLifecycle()
    var showLogs by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showLogs = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("View addon logs (${entries.size})")
    }

    if (!showLogs) return

    AlertDialog(
        onDismissRequest = { showLogs = false },
        title = { Text("Addon diagnostics") },
        text = {
            if (entries.isEmpty()) {
                Text("No addon logs yet. Trigger a series episode from the beginning, then reopen this viewer.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.asReversed().forEach { entry ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "#${entry.id} · ${entry.source}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { AddonDiagnostics.clear() }) { Text("Clear logs") }
                TextButton(onClick = { showLogs = false }) { Text("Close") }
            }
        },
    )
}
