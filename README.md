[![Crowdin](https://badges.crowdin.net/nightwave-plaza/localized.svg)](https://crowdin.com/project/nightwave-plaza)

# 🌃 [Nightwave Plaza](https://plaza.one) Android App

**Nightwave Plaza** is an advertisement-free 24/7 radio station dedicated to Vaporwave; bringing aesthetics and dream-like music to your device wherever you have Internet connectivity.

<a href="https://play.google.com/store/apps/details?id=one.plaza.nightwaveplaza"><img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="75"></a>

APK builds are also available on the [Releases](https://github.com/nightwaveplaza/app-android/releases) page. Requires Android 6.0 or newer.

These are the only official sources. The app's package name is `one.plaza.nightwaveplaza`; anything else using the Nightwave Plaza name in app stores is not ours.

## How it works

The app is a native wrapper: playback, the notification and media controls are handled here, while the interface itself is a web app rendered in a WebView. The web part lives in a separate repository: [nightwaveplaza/plaza](https://github.com/nightwaveplaza/plaza). If you found a bug in the UI, it probably belongs there.

## Building

You need JDK 17. Create `plaza.properties` in the repo root:

```properties
PLAZA_API=https://api.plaza.one/v2
```

The web UI bundle is not stored in git, so download it before the first build:

```bash
./gradlew fetchAndEmbedView
```

After that the project builds as usual from Android Studio.

## 🌍 Translations

Localization is managed via Crowdin. You can help translate the application into your language here: https://crowdin.com/project/nightwave-plaza

## License

The source code is licensed under [GPL-3.0](LICENSE).

The license covers the code only. As permitted by section 7(e) of the GPL, it grants no rights to the Nightwave Plaza name, logo or other branding. If you publish a fork, give it a different name and icon and don't present it as the official app.
