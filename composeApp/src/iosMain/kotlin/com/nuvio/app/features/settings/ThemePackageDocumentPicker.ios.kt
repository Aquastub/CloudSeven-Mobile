package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** Import is intentionally not exposed on iOS until a native document picker is implemented. */
@Composable
internal actual fun ThemePackageDocumentPicker(
    onManifestSelected: (String) -> Unit,
    onError: (String) -> Unit,
) = Unit
