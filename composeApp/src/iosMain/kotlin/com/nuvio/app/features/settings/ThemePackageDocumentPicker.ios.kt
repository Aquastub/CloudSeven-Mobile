package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun ThemePackageDocumentPicker(
    onManifestSelected: (String) -> Unit,
    onError: (String) -> Unit,
) {
    // Intentionally unavailable until the native iOS document-picker bridge is implemented.
}
