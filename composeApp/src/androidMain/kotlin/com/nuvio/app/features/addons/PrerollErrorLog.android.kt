package com.nuvio.app.features.addons

import android.util.Log

/** Android implementation; writes diagnostics to Logcat without affecting playback. */
internal actual fun writePrerollErrorLog(message: String) {
    runCatching {
        Log.w("AddonPreroll", message.take(3_500))
    }
}
