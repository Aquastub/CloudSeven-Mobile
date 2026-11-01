package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.theme.ThemePackageManifest
import com.nuvio.app.core.theme.ThemePackageRepository
import com.nuvio.app.isIos

@Composable
internal fun ThemePackageSettingsSection(isTablet: Boolean) {
    LaunchedEffect(Unit) { ThemePackageRepository.load() }

    val installed by ThemePackageRepository.installed.collectAsStateWithLifecycle()
    val activeId by ThemePackageRepository.activeId.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    SettingsSection(
        title = "Installed themes",
        isTablet = isTablet,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Import a .c7theme package to add a custom look to Cloud 7. Theme packages are declarative and cannot run code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isIos) {
                ThemePackageDocumentPicker(
                    onManifestSelected = { manifestJson ->
                        val result = ThemePackageRepository.install(manifestJson)
                        result.onSuccess { manifest ->
                            ThemePackageRepository.activate(manifest.id)
                            status = "${manifest.name} installed and activated."
                            statusIsError = false
                        }.onFailure { error ->
                            status = error.message ?: "Unable to install this theme."
                            statusIsError = true
                        }
                    },
                    onError = { message ->
                        status = message
                        statusIsError = true
                    },
                )
            } else {
                Text(
                    text = "Theme package import is currently available on Android. You can still manage themes installed on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (status != null) {
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            if (installed.isEmpty()) {
                Text(
                    text = "No imported themes yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                installed.forEach { theme ->
                    ThemePackageRow(
                        theme = theme,
                        isActive = theme.id == activeId,
                        onActivate = {
                            ThemePackageRepository.activate(theme.id)
                            status = "${theme.name} is now active."
                            statusIsError = false
                        },
                        onRemove = {
                            ThemePackageRepository.uninstall(theme.id)
                            status = "${theme.name} was removed."
                            statusIsError = false
                        },
                    )
                }
                if (activeId != null) {
                    OutlinedButton(
                        onClick = {
                            ThemePackageRepository.activate(null)
                            status = "Built-in theme restored."
                            statusIsError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use built-in theme")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePackageRow(
    theme: ThemePackageManifest,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (isActive) "${theme.name} · Active" else theme.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${theme.author} · v${theme.version}\n${theme.description}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isActive) {
                Button(onClick = onActivate, modifier = Modifier.weight(1f)) {
                    Text("Activate")
                }
            }
            OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                Text("Remove")
            }
        }
    }
}
