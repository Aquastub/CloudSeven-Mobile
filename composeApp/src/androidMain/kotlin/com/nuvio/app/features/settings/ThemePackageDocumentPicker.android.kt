package com.nuvio.app.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

private const val MAX_THEME_ARCHIVE_BYTES = 12 * 1024 * 1024
private const val MAX_THEME_MANIFEST_BYTES = 512 * 1024
private const val MAX_THEME_ENTRIES = 256

@Composable
internal actual fun ThemePackageDocumentPicker(
    onManifestSelected: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val archive = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_THEME_ARCHIVE_BYTES) { "Theme archive exceeds the 12 MB limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Unable to read the selected file.")

            var manifest: String? = null
            ZipInputStream(archive.inputStream()).use { zip ->
                var entryCount = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_THEME_ENTRIES) { "Theme archive contains too many files." }
                    val name = entry.name
                    require(!name.startsWith("/") && '\\' !in name && name.split('/').none { it == ".." || it == "." }) {
                        "Theme archive contains an unsafe file path."
                    }
                    if (name == "manifest.json" && !entry.isDirectory) {
                        require(manifest == null) { "Theme archive contains duplicate manifest.json files." }
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var total = 0
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_THEME_MANIFEST_BYTES) { "Theme manifest exceeds 512 KB." }
                            output.write(buffer, 0, count)
                        }
                        manifest = output.toString(Charsets.UTF_8.name())
                    }
                    zip.closeEntry()
                }
            }
            manifest ?: error("No manifest.json was found at the root of this .c7theme archive.")
        }.onSuccess(onManifestSelected).onFailure { error ->
            onError(error.message ?: "Unable to import this theme.")
        }
    }

    Button(onClick = {
        launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }) {
        Text("Import .c7theme")
    }
}
