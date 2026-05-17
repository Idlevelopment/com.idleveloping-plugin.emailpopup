# plugin.emailpopup

Solar2D plugin that opens a real email composer (Gmail, Outlook, ProtonMail, etc.) on Android, pre-filled with recipients, subject, body, and attachments. iOS / macOS / tvOS delegate to Solar2D's built-in `native.showPopup("mail", ...)`, which already works correctly on those platforms.

## Why

Solar2D's stock `native.showPopup("mail", opts)` on Android opens the OS share sheet (`ACTION_SEND` with a wide-open chooser). That chooser surfaces Messages, Drive, Telegram, and every other app that accepts a text payload — defeating the purpose of an email-only popup. This plugin replaces that path with a strict mail-only chooser on Android while keeping the same options table shape on every platform.

## Install

Add to `build.settings`:

```lua
settings = {
    plugins = {
        ["plugin.emailpopup"] = { publisherId = "com.idleveloping" },
    },
}
```

## Usage

```lua
local emailpopup = require("plugin.emailpopup")

emailpopup.show({
    to = "support@example.com",                 -- string or array of strings
    cc = { "cc1@example.com", "cc2@example.com" },
    bcc = "bcc@example.com",
    subject = "Hello from my app",
    body = "Message body here.",
    isBodyHtml = false,
    attachment = {
        baseDir = system.DocumentsDirectory,
        filename = "log.txt",
        type = "text/plain",
    },
})
```

Options table matches Solar2D's built-in mail popup shape. Drop-in replacement: change `native.showPopup("mail", opts)` to `require("plugin.emailpopup").show(opts)`.

### Multiple attachments

```lua
emailpopup.show({
    to = "support@example.com",
    subject = "Crash logs",
    attachment = {
        { baseDir = system.DocumentsDirectory, filename = "log.txt",   type = "text/plain" },
        { baseDir = system.CachesDirectory,    filename = "state.json", type = "application/json" },
    },
})
```

### Supported `baseDir` values

Anything `system.pathForFile` resolves: `system.DocumentsDirectory`, `system.CachesDirectory`, `system.TemporaryDirectory`, `system.ApplicationSupportDirectory`. `system.ResourceDirectory` is unsupported on Android (read-only APK assets cannot be shared via FileProvider without first extracting to a writable location).

## Behaviour

| Platform | Mechanism |
|---|---|
| Android | Builds one `ACTION_SEND` intent per installed mail app (`setPackage(pkg)`), shows a chooser containing only those. No fallback to the share sheet unless zero mail apps are installed. |
| iOS / macOS / tvOS | Calls `native.showPopup("mail", options)` directly. |

Attachments are shared via `FileProvider` with per-recipient `FLAG_GRANT_READ_URI_PERMISSION` so receiving mail apps can actually read the files.

## Building from source

Requires:
- JDK 17 (`temurin-17` recommended)
- Solar2D Native installed at `~/Library/Application Support/Corona/Native/` (or symlinked there)

```bash
cd android
JAVA_HOME=/path/to/jdk-17 ./gradlew :plugin:assembleRelease
```

Output AAR: `android/plugin/build/outputs/aar/plugin-release.aar`.

### Local Solar2DPlugins deployment

Drops the built variants into `~/Solar2DPlugins/com.idleveloping/plugin.emailpopup/` so the Solar2D simulator picks them up ahead of the online plugin directory:

```bash
./gradlew :plugin:deployAllToLocalSolar2DRepo
```

This deploys:
- `android/data.tgz` — Java AAR
- `iphone/`, `iphone-sim/`, `mac-sim/`, `tvos/`, `tvos-sim/` — Lua passthrough variants

Individual platforms can be deployed via `deployToLocalSolar2DRepo` (Android only) or `deployLuaPluginTo_<platform>` for any single Lua variant.

## Repository layout

```
android/
  plugin/                                -- Android Studio module producing the AAR
    src/main/java/plugin/emailpopup/
      LuaLoader.java                     -- Lua entry point, embeds the Android-side Lua shim
      SendEmailWithAttachment.java       -- builds Intent, queries mail apps, shows mail-only chooser
    src/main/AndroidManifest.xml         -- FileProvider + Android 11+ <queries> declaration
    src/main/res/xml/file_paths.xml      -- FileProvider path coverage for every Solar2D baseDir
    build.gradle                         -- AAR build + deployment tasks
plugins/2026.3730/                       -- Distribution layout consumed by Solar2D Plugin Directory
  android/                               --   AAR + metadata.lua + corona.gradle (FileProvider, <queries>)
  iphone/, iphone-sim/, mac-sim/,        --   plugin_emailpopup.lua passthrough + metadata.lua
  tvos/, tvos-sim/                       --   (Solar2D's built-in native.showPopup("mail", ...) works here)
.github/workflows/publish.yml            -- Wraps solar2d/directory-plugin-action; publishes release per push
```

## Publishing to the Solar2D Plugin Directory

This repository ships with the layout and GitHub Action that the
[Solar2D Free Plugin Directory](https://plugins.solar2d.com/) expects.

### One-time setup

1. **Repository name must match the directory convention.** The directory's
   [`json_from_repo.py`](https://github.com/solar2d/plugins.solar2d.com/blob/master/json_from_repo.py)
   parses the repo name as `<publisherId>-<plugin.name>`, splitting on the
   first hyphen. Rename this repo to
   **`com.idleveloping-plugin.emailpopup`** before publishing — otherwise the
   automated directory entry generation will pick wrong values.
2. **Open a submission issue** at
   [solar2d/plugins.solar2d.com/issues/new](https://github.com/solar2d/plugins.solar2d.com/issues/new)
   describing the plugin and requesting `publisherId = com.idleveloping`,
   `plugin.name = plugin.emailpopup`. Wait for maintainer approval.
3. **(Optional) Add the `ISSUE_PAT` secret** to this repository's GitHub
   settings. Personal access token with `repo` scope. Without it the workflow
   still creates a release, but the directory entry won't auto-refresh — you'd
   PR the JSON update manually each time.

### Release flow

Every push to `main` that touches `plugins/**` triggers
`.github/workflows/publish.yml`, which:

1. Tars each `plugins/<build>/<platform>/` directory into
   `<build>-<platform>.tgz`.
2. Creates a GitHub release named `v<run_number>` with those tarballs as
   assets.
3. Dispatches a refresh request to `solar2d/plugins.solar2d.com` (only if
   `ISSUE_PAT` is set) so the directory JSON regenerates against the latest
   release.

### Cutting a new release

After changing Android code:

```bash
cd android
./gradlew :plugin:refreshDirectoryPlugin     # builds AAR + copies into plugins/2026.3730/android/
git add plugins/                              # commit the rebuilt AAR + any lua/metadata changes
git commit -m "Release notes"
git push origin main                          # GitHub Action takes over
```

After changing Lua-only platform code (`plugins/2026.3730/*/plugin_emailpopup.lua`):
just commit and push — the workflow tars and releases.

### Supporting additional Solar2D builds

Duplicate `plugins/2026.3730/` to e.g. `plugins/2024.3703/`, rebuild the AAR
against that Solar2D Native version, and update `solar2dBuild` in
`android/plugin/build.gradle` if you want the local-deploy tasks to target
the new build.

## Android internals

The Android variant works around two Solar2D / Android quirks that the stock mail popup hits:

1. **Selector intents are honored inconsistently.** Setting an `ACTION_SENDTO` `mailto:` selector on an `ACTION_SEND` intent does not reliably constrain the chooser to mail apps. Instead the plugin enumerates mail handlers with `queryIntentActivities(ACTION_SENDTO, mailto:)`, then builds one explicit `ACTION_SEND` intent per discovered package and combines them into a single chooser via `EXTRA_INITIAL_INTENTS`.
2. **Android 11+ package visibility.** Without an explicit `<queries>` block declaring the `mailto:` intent, `queryIntentActivities` returns empty on API 30+ even when mail apps exist. The plugin's manifest declares this — host apps inherit it automatically through manifest merging.

## License

MIT — see [LICENSE](LICENSE).
