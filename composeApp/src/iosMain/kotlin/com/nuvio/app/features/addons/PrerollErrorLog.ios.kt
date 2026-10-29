package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger

/** iOS implementation; Kermit routes messages to the platform logging destination. */
internal actual fun writePrerollErrorLog(message: String) {
    runCatching {
        Logger.withTag("AddonPreroll").w { message }
    }
}
