# Cloud 7 `.c7theme` integration

This source snapshot adds the `.c7theme` manifest registry and the **Settings → Appearance → Installed themes** section.

## Included
- Manifest schema v1 validation and decoding.
- Profile-scoped installed-theme registry and active-theme persistence on Android and iOS.
- Android document picker for `.c7theme` ZIP files, with archive/path/manifest size limits.
- Installed theme list with Activate, Remove, and Use built-in theme actions.
- Active package color palette, layout-token, and component-token integration into `NuvioTheme`.

## Current limitations
- Importing the archive currently reads and installs the root `manifest.json`; packaged asset files are not extracted or loaded.
- Theme tokens only affect the palette and the renderer hooks currently exposed by the app. Typography and arbitrary layout/asset replacement are not yet implemented.
- The iOS UI can manage themes already present in local storage, but importing packages is Android-only in this integration.

## Verification
The Android build was attempted with `bash gradlew :androidApp:assembleFullDebug --no-daemon`, but Gradle 9.4.1 could not be downloaded because this environment has no DNS/network access to `services.gradle.org`. Therefore this archive is source-integrated but **not build-verified**.
