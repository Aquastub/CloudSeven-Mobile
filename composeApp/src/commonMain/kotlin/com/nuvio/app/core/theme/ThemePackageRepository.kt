package com.nuvio.app.core.theme

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import com.nuvio.app.features.settings.ThemeSettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Profile-scoped registry for installed .c7theme manifests. */
object ThemePackageRepository {
    private val json = Json { ignoreUnknownKeys = false }
    private val _installed = MutableStateFlow<List<ThemePackageManifest>>(emptyList())
    val installed: StateFlow<List<ThemePackageManifest>> = _installed.asStateFlow()
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()
    private val _activePackage = MutableStateFlow<ThemePackageManifest?>(null)
    val activePackage: StateFlow<ThemePackageManifest?> = _activePackage.asStateFlow()
    private var loaded = false

    fun reload() { loaded = false; load() }

    fun load() {
        if (loaded) return
        loaded = true
        _installed.value = runCatching {
            json.decodeFromString(ListSerializer(ThemePackageManifest.serializer()), ThemeSettingsStorage.loadInstalledThemePackages().orEmpty())
                .also { list -> list.forEach(ThemePackageCodec::validate) }
        }.getOrDefault(emptyList())
        _activeId.value = ThemeSettingsStorage.loadActiveThemePackageId()?.takeIf { id -> _installed.value.any { it.id == id } }
        _activePackage.value = _installed.value.firstOrNull { it.id == _activeId.value }
        if (_activeId.value == null) ThemeSettingsStorage.saveActiveThemePackageId(null)
    }

    fun install(manifestJson: String): Result<ThemePackageManifest> = runCatching {
        load()
        val manifest = ThemePackageCodec.decode(manifestJson).getOrThrow()
        val updated = _installed.value.filterNot { it.id == manifest.id } + manifest
        persist(updated)
        if (_activeId.value == manifest.id) _activePackage.value = manifest
        manifest
    }

    fun activate(themeId: String?): Boolean {
        load()
        if (themeId != null && _installed.value.none { it.id == themeId }) return false
        _activeId.value = themeId
        ThemeSettingsStorage.saveActiveThemePackageId(themeId)
        _activePackage.value = _installed.value.firstOrNull { it.id == themeId }
        return true
    }

    fun uninstall(themeId: String): Boolean {
        load()
        if (_installed.value.none { it.id == themeId }) return false
        persist(_installed.value.filterNot { it.id == themeId })
        if (_activeId.value == themeId) activate(null)
        return true
    }

    private fun persist(packages: List<ThemePackageManifest>) {
        _installed.value = packages
        ThemeSettingsStorage.saveInstalledThemePackages(json.encodeToString(ListSerializer(ThemePackageManifest.serializer()), packages))
    }
}
