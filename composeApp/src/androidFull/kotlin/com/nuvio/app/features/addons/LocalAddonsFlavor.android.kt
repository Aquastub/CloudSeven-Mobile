package com.nuvio.app.features.addons

import android.content.Context

/** Local addons, addon programs and addon UI that only ship in the full distribution. */
internal fun registerFlavorLocalAddons(context: Context) {
    ZipAddonPlatform.initialize(context)
    AddonProgramPlatform.initialize(context)
    ZipAddonManager.loadInstalled()
    LocalAddonUi.importContent = { ZipAddonImportSection() }
    LocalAddonUi.settingsContent = { manifestUrl -> AddonSettingsEntry(manifestUrl) }
    LocalAddonUi.prerollProvider = { request -> AddonPreroll.request(request) }
    LocalAddonUi.playerActions = { playerContext, modifier -> AddonPlayerActions(playerContext, modifier) }
}
