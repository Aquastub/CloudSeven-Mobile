package com.nuvio.app.features.addons

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Asks installed addons with the "preroll" permission for a clip to play before an episode.
 * The addon receives `{ resource: "preroll", id: <video id>, extra: { title, season, episode, ... } }`
 * and answers `{ clips: [{ url, headers?, title? }, ...] }`. Legacy `{ url, headers? }` replies remain supported.
 */
internal object AddonPreroll {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun request(request: PrerollRequest): List<PrerollClip>? {
        val addons = LocalAddonRegistry.all()
        log("Lookup started: contentType=${request.contentType ?: "series"}, season=${request.seasonNumber}, episode=${request.episodeNumber}, title=${request.title.take(100)}, hasVideoId=${!request.videoId.isNullOrBlank()}, addons=${addons.size}")

        val extra = buildMap {
            put("title", request.title)
            put("contentType", request.contentType.orEmpty())
            request.seasonNumber?.let { put("season", it.toString()) }
            request.episodeNumber?.let { put("episode", it.toString()) }
            request.episodeTitle?.let { put("episodeTitle", it) }
            put("parentMetaId", request.parentMetaId)
        }

        if (addons.isEmpty()) log("No addons are registered")
        for (addon in addons) {
            if (PERMISSION_PREROLL !in addon.permissions) {
                log("Skipping addon '${addon.slug}': missing preroll permission")
                continue
            }

            val raw = try {
                addon.handle(
                    LocalAddonRequest(
                        resource = "preroll",
                        type = request.contentType ?: "series",
                        id = request.videoId.orEmpty(),
                        extra = extra,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log("Addon '${addon.slug}' threw ${error::class.simpleName}: ${error.message.orEmpty().take(240)}")
                continue
            }

            val clips = parse(raw, addon.slug)
            if (!clips.isNullOrEmpty()) {
                log("Selected ${clips.size} preroll clip(s) from addon '${addon.slug}'")
                return clips
            }
        }

        log("Lookup finished: no valid preroll clip")
        return null
    }

    private fun parse(raw: String, addonId: String): List<PrerollClip>? {
        val element = try {
            json.parseToJsonElement(raw)
        } catch (error: Throwable) {
            log("Addon '$addonId' returned invalid JSON: ${error.message.orEmpty().take(180)}")
            return null
        }
        val obj = element as? JsonObject
        if (obj == null) {
            log("Addon '$addonId' response was not a JSON object")
            return null
        }

        // New sequence format: { "clips": [{ "url": "...", "headers": {}, "title": "..." }] }
        // Keep the old single-clip format working for existing addons.
        val entries = (obj["clips"] as? JsonArray)?.toList()
            ?: listOf(element)
        val clips = entries.mapNotNull { entry ->
            val clipObject = entry as? JsonObject ?: return@mapNotNull null
            val url = (clipObject["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (url.isBlank()) return@mapNotNull null
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                log("Addon '$addonId' returned a non-HTTP(S) clip URL")
                return@mapNotNull null
            }
            val headers = (clipObject["headers"] as? JsonObject)?.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }?.toMap().orEmpty()
            val title = (clipObject["title"] as? JsonPrimitive)?.contentOrNull?.trim()?.take(100)
            PrerollClip(url = url, headers = headers, title = title)
        }.take(MAX_PREROLL_CLIPS)

        if (clips.isEmpty()) log("Addon '$addonId' returned no valid preroll clips")
        return clips.takeIf { it.isNotEmpty() }
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').take(160)

    private const val MAX_PREROLL_CLIPS = 24

    private fun log(message: String) {
        runCatching { AddonDiagnostics.record("Preroll", message) }
        runCatching { writePrerollErrorLog(message) }
    }
}
