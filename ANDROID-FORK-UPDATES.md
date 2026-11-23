# Litefin fork Android updates

The **Fork Android release** workflow checks upstream stable releases hourly.
It merges the release tag into the fork's `development` branch, builds and
verifies a universal APK, and publishes `Litefin-Android-universal.apk` under
the same tag. The branch is only pushed after the APK is verified and signed.
Merge conflicts and build failures require intervention.

Use a manual run with **Publish** off to test the pipeline without publishing.
The schedule skips the initial upstream baseline, `v2.10.0`; select **Publish**
in a manual run to release that version. The signing key is stored separately
from the repo and provided to the signing job through Actions secrets.

The first updater-enabled build must be installed manually. Android only
updates an existing installation with a matching application ID and signing
key. An official Litefin installation cannot be updated in place using the
fork's signing key. The fork uses `org.litefin.wheemer` so it can be installed
alongside official Litefin and earlier test builds.

Litefin checks automatically on startup unless disabled. To install, open
Settings > About and select Check for Updates. The first install may require
allowing Litefin to install unknown apps in Android settings. Android still
asks for confirmation; the updater does not silently install.
