package com.nuvio.app.features.plugins.runtime.host

import co.touchlab.kermit.Logger
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.delay

private const val MAX_PLUGIN_TIMER_DELAY_MS = 60_000L

internal class HostFunctions(
    private val scraperId: String,
    private val scraperSettingsJson: String,
    private val callArgsJson: String = "{}",
    private val onResult: (String) -> Unit,
    private val onLog: (level: String, message: String) -> Unit = { _, _ -> },
) : HostModule {
    private val log = Logger.withTag("PluginRuntime")

    override fun register(runtime: QuickJs) {
        runtime.asyncFunction("__plugin_sleep") { args: Array<Any?> ->
            val durationMs = (args.getOrNull(0) as? Number)
                ?.toLong()
                ?.coerceIn(0L, MAX_PLUGIN_TIMER_DELAY_MS)
                ?: 0L
            delay(durationMs)
            null
        }

        runtime.define("console") {
            function("log") { args -> reportConsole("log", args); null }
            function("error") { args -> reportConsole("error", args); null }
            function("warn") { args -> reportConsole("warn", args); null }
            function("info") { args -> reportConsole("info", args); null }
            function("debug") { args -> reportConsole("debug", args); null }
        }

        runtime.function("__get_scraper_id") { scraperId }
        runtime.function("__get_scraper_settings") { scraperSettingsJson }
        runtime.function("__get_tmdb_api_key") { TmdbSettingsRepository.effectiveApiKey() }
        runtime.function("__get_call_args") { callArgsJson }
        runtime.function("__capture_result") { args ->
            onResult(args.getOrNull(0)?.toString() ?: "[]")
            null
        }
    }

    private fun reportConsole(level: String, args: Array<Any?>) {
        val message = args.joinToString(" ") { it?.toString() ?: "null" }
        val taggedMessage = "Plugin:$scraperId $message"
        when (level) {
            "error" -> log.e { taggedMessage }
            "warn" -> log.w { taggedMessage }
            "info" -> log.i { taggedMessage }
            else -> log.d { taggedMessage }
        }
        runCatching { onLog(level, message) }
    }
}
