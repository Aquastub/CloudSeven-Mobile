package com.nuvio.app.features.addons

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AddonDiagnosticEntry(
    val id: Long,
    val source: String,
    val message: String,
)

/** Bounded in-memory diagnostic buffer shared by preroll and local JavaScript addons. */
object AddonDiagnostics {
    private const val MAX_ENTRIES = 500
    private const val MAX_MESSAGE_LENGTH = 2_000

    private val mutableEntries = MutableStateFlow<List<AddonDiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<AddonDiagnosticEntry>> = mutableEntries.asStateFlow()

    fun record(source: String, message: String) {
        val safeSource = source.take(100)
        val safeMessage = message.take(MAX_MESSAGE_LENGTH)
        mutableEntries.update { current ->
            val nextId = (current.lastOrNull()?.id ?: 0L) + 1L
            (current + AddonDiagnosticEntry(nextId, safeSource, safeMessage)).takeLast(MAX_ENTRIES)
        }
    }

    fun clear() {
        mutableEntries.value = emptyList()
    }
}
