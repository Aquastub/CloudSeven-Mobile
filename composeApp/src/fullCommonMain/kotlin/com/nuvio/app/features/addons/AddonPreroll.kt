package com.nuvio.app.features.addons

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Asks installed addons with the "preroll" permission for a clip to play before an episode.
 * The addon receives `{ resource: "preroll", id: <video id>, extra: { title, season, episode, ... } }`
 * and answers `{ url, headers? }` (or `{}` for "no clip this time"). The first addon with a clip wins.
 */
internal object AddonPreroll {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun request(request: PrerollRequest): PrerollClip? {
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

            val clip = parse(raw, addon.slug)
            if (clip != null) {
                log("Selected preroll from addon '${addon.slug}', host=${hostOf(clip.url)}")
                return clip
            }
        }

        log("Lookup finished: no valid preroll clip")
        return null
    }

    private fun parse(raw: String, addonId: String): PrerollClip? {
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

        val url = (obj["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (url.isBlank()) {
            log("Addon '$addonId' returned no clip URL")
            return null
        }
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            log("Addon '$addonId' returned a non-HTTP(S) URL")
            return null
        }

        val headers = (obj["headers"] as? JsonObject)?.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }?.toMap().orEmpty()
        return PrerollClip(url = url, headers = headers)
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').take(160)

    private fun log(message: String) {
        runCatching { AddonDiagnostics.record("Preroll", message) }
        runCatching { writePrerollErrorLog(message) }
    }
}
