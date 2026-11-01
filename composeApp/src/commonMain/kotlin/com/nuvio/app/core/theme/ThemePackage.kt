package com.nuvio.app.core.theme

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Declarative .c7theme manifest. A package is a ZIP with this file at its root. */
@Serializable
data class ThemePackageManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val author: String = "Unknown",
    val description: String = "",
    val version: String = "1.0.0",
    val minAppVersion: String = "0.0.0",
    val colors: Map<String, String> = emptyMap(),
    val typography: Map<String, String> = emptyMap(),
    val assets: Map<String, String> = emptyMap(),
    val layout: Map<String, String> = emptyMap(),
    /** Declarative component tokens, e.g. poster.cornerRadiusDp or navigation.style. */
    val components: Map<String, String> = emptyMap(),
)

object ThemePackageCodec {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; prettyPrint = true }
    private val idPattern = Regex("[a-z0-9][a-z0-9._-]{1,63}")
    private val colorPattern = Regex("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

    fun encode(manifest: ThemePackageManifest): String = json.encodeToString(ThemePackageManifest.serializer(), manifest)

    fun decode(source: String): Result<ThemePackageManifest> = runCatching {
        val manifest = json.decodeFromString(ThemePackageManifest.serializer(), source)
        validate(manifest)
        manifest
    }

    fun validate(manifest: ThemePackageManifest) {
        require(manifest.schemaVersion == 1) { "Unsupported .c7theme schema version: ${manifest.schemaVersion}" }
        require(idPattern.matches(manifest.id)) { "Theme ID must be 2–64 lowercase letters, numbers, dots, underscores or hyphens." }
        require(manifest.name.isNotBlank() && manifest.name.length <= 80) { "Theme name is required and must be at most 80 characters." }
        require(manifest.author.length <= 80 && manifest.description.length <= 500) { "Theme metadata is too long." }
        require(manifest.colors.size <= 64 && manifest.typography.size <= 32 && manifest.assets.size <= 64 && manifest.layout.size <= 64 && manifest.components.size <= 128) { "Theme contains too many entries." }
        manifest.colors.forEach { (key, value) ->
            require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,63}"))) { "Invalid color token: $key" }
            require(colorPattern.matches(value)) { "Invalid color value for $key" }
        }
        manifest.assets.forEach { (key, path) ->
            require(key.isNotBlank() && path.isNotBlank()) { "Asset names and paths cannot be empty." }
            require(!path.startsWith('/') && '\\' !in path && path.split('/').none { it == ".." || it == "." }) { "Unsafe asset path: $path" }
        }
        manifest.layout.forEach { (key, value) ->
            require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,63}"))) { "Invalid layout token: $key" }
            require(value.length <= 120) { "Layout value is too long." }
        }
        manifest.components.forEach { (key, value) ->
            require(key.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,95}"))) { "Invalid component token: $key" }
            require(value.length <= 120) { "Component token value is too long." }
        }
    }
}


/** Converts the safe, declarative manifest tokens into the app's existing Compose palette. */
fun ThemePackageManifest.toColorPalette(base: com.nuvio.app.core.ui.ThemeColorPalette): com.nuvio.app.core.ui.ThemeColorPalette {
    fun color(vararg keys: String, fallback: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
        val raw = keys.firstNotNullOfOrNull { colors[it] } ?: return fallback
        val hex = raw.removePrefix("#").toLongOrNull(16) ?: return fallback
        return if (raw.length == 9) androidx.compose.ui.graphics.Color(hex.toULong().toLong())
        else androidx.compose.ui.graphics.Color(0xFF000000L or hex)
    }
    val accent = color("accent", "secondary", fallback = base.secondary)
    val accentAlt = color("accentSecondary", "secondaryVariant", fallback = base.secondaryVariant)
    val background = color("background", fallback = base.background)
    val surface = color("surface", "backgroundElevated", fallback = base.backgroundElevated)
    val card = color("card", "surfaceVariant", "backgroundCard", fallback = base.backgroundCard)
    val focus = color("focus", "focusRing", fallback = base.focusRing)
    val focusBg = color("focusBackground", fallback = base.focusBackground)
    return base.copy(secondary = accent, secondaryVariant = accentAlt,
        accentGradient = listOf(accent, accentAlt), nativeAccentHex = "#" + accent.value.toString(16).takeLast(6).uppercase(),
        background = background, backgroundElevated = surface, backgroundCard = card,
        focusRing = focus, focusBackground = focusBg)
}
