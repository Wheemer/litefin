/**
 * ============================================================================
 * Litefin Tizen - Version Checker
 * ============================================================================
 * Checks github releases for a new version to notify the user.
 * ============================================================================
 */

import { toast } from '../ui/Toast.js';
import { i18n } from './i18n.js';
import { logger } from './Logger.js';
import { storage } from './StorageService.js';
import { idleGate } from './IdleGate.js';

const log = logger.create('VersionChecker');

class VersionChecker {
    constructor() {
        this.apiEndpoint = 'https://api.github.com/repos/MoazSalem/litefin/releases/latest';
    }

    /**
     * Helper to parse semver strings (e.g. v1.2.3 -> [1, 2, 3])
     */
    _parseVersion(versionString) {
        if (!versionString) return [0, 0, 0];
        const stripped = versionString.replace(/v/i, '').split('.');
        return stripped.map((num) => parseInt(num, 10) || 0);
    }

    /**
     * Returns true if remote is newer than local
     */
    _isNewer(remoteStr, localStr) {
        const remote = this._parseVersion(remoteStr);
        const local = this._parseVersion(localStr);

        for (let i = 0; i < Math.max(remote.length, local.length); i++) {
            const r = remote[i] || 0;
            const l = local[i] || 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        const isPrerelease = (version) => /^[vV]?\d+\.\d+\.\d+-/.test(version);
        return isPrerelease(localStr) && !isPrerelease(remoteStr);
    }

    async checkUpdate(manual = false) {
        try {
            log.info('Checking for updates from GitHub...');
            const updater = globalThis.LitefinUpdater;
            const endpoint = updater
                ? 'https://api.github.com/repos/Wheemer/litefin/releases/latest'
                : this.apiEndpoint;
            const response = await fetch(endpoint, {
                method: 'GET',
                headers: {
                    Accept: 'application/vnd.github.v3+json'
                }
            });

            if (!response.ok) {
                if (updater && response.status === 404) {
                    if (manual) toast.show('No fork update has been published yet.', 3000);
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            const latestVersion = data.tag_name;
            // __APP_VERSION__ is injected globally by Webpack
            const currentVersion = __APP_VERSION__;

            log.debug(`Latest remote version: ${latestVersion}, Current local version: ${currentVersion}`);

            if (latestVersion && this._isNewer(latestVersion, currentVersion)) {
                if (updater) {
                    if (!data.assets?.some(asset => asset.name === 'Litefin-Android-universal.apk')) {
                        if (manual) toast.show('This release has no universal APK.', 3000);
                        return;
                    }
                    if (manual) {
                        updater.offerUpdate(latestVersion);
                        return;
                    }
                    toast.show('Litefin update available. Open Settings > About to install.', 5000);
                    return;
                }
                // Show update availability toast
                const message = (i18n.t('NewVersionMessage') || 'Version {0} is available on GitHub (Current: {1})')
                    .replace('{0}', latestVersion)
                    .replace('{1}', currentVersion);

                const title = i18n.t('NewVersionAvailable') || 'New Update Available!';

                // Display notification with a concise 5-second auto-dismiss window
                toast.show(`${title} — ${message}`, 5000, {
                    id: 'app-update',
                    replace: true,
                    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="var(--jf-btn-primary-bg, #00a4dc)"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>`
                });
            } else if (manual) {
                toast.show(i18n.t('AppIsUpToDate') || 'Litefin is up to date.', 3000, {
                    icon: `<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>`
                });
            }
        } catch (error) {
            log.error('Failed to check for updates', error);
            if (manual) {
                toast.show(i18n.t('UpdateCheckFailed') || 'Could not check for updates.', 3000);
            }
        }
    }

    checkAtStartup() {
        const shouldCheck = storage.getItem('pref:checkForUpdates');
        // Defaults to true, so we only skip if explicitly 'false'
        if (shouldCheck === 'false') {
            log.info('Auto update check is disabled by user.');
            return;
        }

        // Route startup check through idleGate so network fetching, JSON parsing,
        // and toast UI rendering do not collide with boot rendering or initial D-pad scrolling.
        // Requires a 5-second quiet window, bounded by a 30-second maximum postponement.
        idleGate.runWhenIdle(() => this.checkUpdate(false), 5000, 30000);
    }
}

export const versionChecker = new VersionChecker();
