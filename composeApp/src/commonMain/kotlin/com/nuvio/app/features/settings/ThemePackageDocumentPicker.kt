package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** Platform document picker for declarative .c7theme ZIP packages. */
@Composable
internal expect fun ThemePackageDocumentPicker(
    onManifestSelected: (String) -> Unit,
    onError: (String) -> Unit,
)
