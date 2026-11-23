package org.litefin.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

internal class AndroidUpdater(private val activity: Activity) {
    private val busy = AtomicBoolean()
    private val maxBytes = 512L * 1024 * 1024

    @JavascriptInterface
    fun offerUpdate(tag: String) {
        if (!Regex("""v\d{1,6}\.\d{1,6}\.\d{1,6}""").matches(tag)) return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed || busy.get()) return@runOnUiThread
            AlertDialog.Builder(activity)
                .setTitle("Update Litefin to $tag?")
                .setMessage("Download the universal APK and open Android's installer.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Download") { _, _ -> startUpdate(tag) }
                .show()
        }
    }

    private fun startUpdate(tag: String) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setMessage("Allow Litefin to install updates, then check for updates again.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Settings") { _, _ ->
                    runCatching {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                    }.onFailure { notifyUser("Open Android settings to allow Litefin to install updates.") }
                }.show()
            return
        }
        if (!busy.compareAndSet(false, true)) return
        notifyUser("Downloading Litefin update...")
        thread(name = "litefin-update") {
            val folder = File(activity.cacheDir, "apk-updates")
            val apk = File(folder, "Litefin-Android-universal.apk")
            try {
                folder.mkdirs()
                download(URL("https://github.com/Wheemer/litefin/releases/download/$tag/${apk.name}"), apk)
                verify(apk)
                activity.runOnUiThread {
                    try {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
                        activity.startActivity(Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (e: Exception) {
                        notifyUser("Could not open Android's installer.")
                    } finally {
                        busy.set(false)
                    }
                }
            } catch (e: Exception) {
                apk.delete()
                busy.set(false)
                notifyUser("Update failed: ${e.message ?: "could not download APK"}")
            }
        }
    }

    private fun download(initial: URL, apk: File) {
        var url = initial
        repeat(5) {
            require(url.protocol == "https" && url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            try {
                when (val status = connection.responseCode) {
                    301, 302, 303, 307, 308 -> url = URL(url, checkNotNull(connection.getHeaderField("Location")))
                    200 -> {
                        require(connection.contentLengthLong <= maxBytes) { "APK is too large" }
                        connection.inputStream.use { input ->
                            apk.outputStream().buffered().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= maxBytes) { "APK is too large" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        return
                    }
                    else -> error("GitHub download returned HTTP $status")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many download redirects")
    }

    @Suppress("DEPRECATION")
    private fun verify(apk: File) {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val candidate = checkNotNull(pm.getPackageArchiveInfo(apk.path, flags)) { "Invalid APK" }
        val installed = pm.getPackageInfo(activity.packageName, flags)
        fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else info.versionCode.toLong()
        fun signers(info: PackageInfo) = (if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners else info.signatures)?.toSet().orEmpty()
        require(candidate.packageName == installed.packageName) { "APK belongs to a different app" }
        require(version(candidate) > version(installed)) { "APK is not newer than the installed app" }
        val expected = signers(installed)
        require(expected.isNotEmpty() && signers(candidate) == expected) { "APK signature does not match" }
    }

    private fun notifyUser(message: String) = activity.runOnUiThread {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
