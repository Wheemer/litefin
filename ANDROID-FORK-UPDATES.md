# Litefin fork Android updates

Publish one signed universal APK as `Litefin-Android-universal.apk` on a
published, non-prerelease `vMAJOR.MINOR.PATCH` release in `Wheemer/litefin`.
The tag must match the built app version. Keep the application ID and release
signing key fixed, and increase Android's version code on each release.

The first updater-enabled build must be installed manually. Android only
updates an existing installation with a matching application ID and signing
key. An official Litefin installation cannot be updated in place using the
fork's signing key.

Litefin checks automatically on startup unless disabled. To install, open
Settings > About and select Check for Updates. The first install may require
allowing Litefin to install unknown apps in Android settings. Android still
asks for confirmation; the updater does not silently install.
