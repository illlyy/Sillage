package com.termux.app.update

import org.json.JSONObject
import java.net.URI

internal data class AppUpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val force: Boolean,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
) {
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("minSdk", minSdk)
        .put("force", force)
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("changelog", changelog)
        .toString()

    fun apkFileName(): String {
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "Fcode-$safeVersion.apk"
    }

    companion object {
        private val SHA_256 = Regex("^[0-9a-fA-F]{64}$")

        fun parse(json: String): AppUpdateManifest {
            val root = JSONObject(json)
            val versionCode = root.optLong("versionCode", -1L)
            val versionName = root.optString("versionName").trim()
            val minSdk = root.optInt("minSdk", 24)
            val apkUrl = root.optString("apkUrl").trim()
            val sha256 = root.optString("sha256").trim().lowercase()
            val changelog = root.optString("changelog").trim()
            val uri = runCatching { URI(apkUrl) }.getOrNull()

            require(versionCode > 0L) { "versionCode must be positive" }
            require(versionName.isNotBlank()) { "versionName is missing" }
            require(minSdk > 0) { "minSdk must be positive" }
            require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
                "apkUrl must be an HTTPS URL"
            }
            require(SHA_256.matches(sha256)) { "sha256 must contain 64 hexadecimal characters" }

            return AppUpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                minSdk = minSdk,
                force = root.optBoolean("force", false),
                apkUrl = apkUrl,
                sha256 = sha256,
                changelog = changelog,
            )
        }
    }
}
