package com.example.core.network

import com.example.BuildConfig
import com.example.core.logging.AppLogger
import com.example.data.model.XrayCoreProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val isNewer: Boolean
)

object GitHubUpdateChecker {

    private const val FINIVPN_LATEST_RELEASE_URL =
        "https://api.github.com/repos/TheZenitiK/FiniVPN/releases/latest"
    private const val XRAY_CORE_RELEASES_URL =
        "https://api.github.com/repos/XTLS/Xray-core/releases?per_page=8"

    private val client: OkHttpClient by lazy {
        SharedHttpClient.baseClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkLatestAppRelease(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            AppLogger.log("UPDATE", "Проверка обновлений GitHub: $FINIVPN_LATEST_RELEASE_URL")
            val req = Request.Builder()
                .url(FINIVPN_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FINI/${BuildConfig.VERSION_NAME}")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    AppLogger.log("UPDATE", "Релизы пока не найдены (404)")
                    return@use null
                }
                if (!resp.isSuccessful) error("GitHub HTTP ${resp.code}")
                val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                val json = JSONObject(body)
                val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                if (tag.isBlank()) return@use null
                val cleanRemote = tag.trimStart('v', 'V')
                val htmlUrl = json.optString("html_url", "https://github.com/TheZenitiK/FiniVPN/releases")
                val notes = json.optString("body", "")
                var preferredFiniApkUrl: String? = null
                var fallbackApkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        val dlUrl = asset.optString("browser_download_url")
                        if (name.equals("Fini.apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                            preferredFiniApkUrl = dlUrl
                            break
                        }
                        if (fallbackApkUrl == null && name.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                            fallbackApkUrl = dlUrl
                        }
                    }
                }
                val apkUrl = preferredFiniApkUrl ?: fallbackApkUrl
                val isNewer = compareVersions(cleanRemote, BuildConfig.VERSION_NAME) > 0
                AppLogger.log(
                    "UPDATE",
                    "Найден релиз $tag (новая=$isNewer, Fini.apk=${preferredFiniApkUrl != null})"
                )
                AppReleaseInfo(
                    tagName = tag,
                    versionName = cleanRemote,
                    releaseNotes = notes,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    isNewer = isNewer
                )
            }
        }.onFailure { err ->
            AppLogger.log("UPDATE", "Ошибка проверки обновлений: ${err.localizedMessage ?: err.javaClass.simpleName}")
        }
    }

    suspend fun fetchAvailableXrayCoreReleases(): Result<List<XrayCoreProfile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(XRAY_CORE_RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "FINI/${BuildConfig.VERSION_NAME}")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("GitHub HTTP ${resp.code}")
                    val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: "[]"
                    val arr = JSONArray(body)
                    val list = mutableListOf<XrayCoreProfile>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val tag = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
                        list.add(
                            XrayCoreProfile(
                                id = "xray_$tag",
                                versionTag = tag,
                                title = "Xray-core $tag",
                                isBuiltIn = false,
                                releaseNotes = obj.optString("name", tag)
                            )
                        )
                    }
                    list
                }
            }
        }

    suspend fun downloadXrayCoreReleaseArchive(
        profile: XrayCoreProfile,
        coresDir: java.io.File,
        onProgress: (Float) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!coresDir.exists()) coresDir.mkdirs()
            val releaseApiUrl = "https://api.github.com/repos/XTLS/Xray-core/releases/tags/${profile.versionTag}"
            val metaReq = Request.Builder()
                .url(releaseApiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FINI/${BuildConfig.VERSION_NAME}")
                .build()

            var assetUrl: String? = null
            client.newCall(metaReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name")
                            if (name.contains("android-arm64", ignoreCase = true) && name.endsWith(".zip", ignoreCase = true)) {
                                assetUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                        if (assetUrl == null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.optJSONObject(i) ?: continue
                                val name = asset.optString("name")
                                if (name.endsWith(".zip", ignoreCase = true)) {
                                    assetUrl = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                    }
                    if (assetUrl.isNullOrBlank()) {
                        assetUrl = json.optString("zipball_url").takeIf { it.isNotBlank() }
                    }
                }
            }

            val downloadUrl = assetUrl
                ?: "https://github.com/XTLS/Xray-core/archive/refs/tags/${profile.versionTag}.zip"

            val outFile = java.io.File(coresDir, "${profile.id}.zip")
            val dlReq = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "FINI/${BuildConfig.VERSION_NAME}")
                .build()

            onProgress(0.05f)
            client.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Пустой ответ от сервера")
                val total = body.contentLength()
                var downloaded = 0L
                val buf = ByteArray(8192)
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        var read = input.read(buf)
                        while (read != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0L) {
                                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0.05f, 0.99f))
                            } else {
                                onProgress((downloaded / 2_000_000f).coerceIn(0.05f, 0.95f))
                            }
                            read = input.read(buf)
                        }
                        output.flush()
                    }
                }
            }
            onProgress(1f)
            outFile
        }
    }

    suspend fun downloadAppUpdateApk(
        downloadUrl: String,
        destFile: java.io.File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        runCatching {
            destFile.parentFile?.let { dir ->
                if (!dir.exists()) dir.mkdirs()
                dir.listFiles()?.forEach { old ->
                    if (old.isFile && (old.name.endsWith(".apk") || old.name.endsWith(".part"))) {
                        old.delete()
                    }
                }
            }
            val partFile = java.io.File(destFile.parentFile, "${destFile.name}.part")
            val dlClient = client.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "FINI/${BuildConfig.VERSION_NAME}")
                .build()

            AppLogger.log("UPDATE", "Скачивание APK обновления: $downloadUrl")
            onProgress(0f, 0L, -1L)
            try {
                var lastNotifiedAt = 0L
                dlClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("Пустой ответ от сервера")
                    val total = body.contentLength()
                    var downloaded = 0L
                    val buf = ByteArray(16384)
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            var read = input.read(buf)
                            while (read != -1) {
                                currentCoroutineContext().ensureActive()
                                output.write(buf, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastNotifiedAt >= 120L || (total > 0L && downloaded >= total)) {
                                    lastNotifiedAt = now
                                    val fraction = if (total > 0L) {
                                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                                    } else {
                                        (downloaded / 25_000_000f).coerceIn(0.02f, 0.95f)
                                    }
                                    onProgress(fraction, downloaded, total)
                                }
                                read = input.read(buf)
                            }
                            output.flush()
                        }
                    }
                }
                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }
                val finalSize = destFile.length()
                onProgress(1f, finalSize, finalSize)
                AppLogger.log("UPDATE", "APK скачан: ${destFile.name} ($finalSize байт)")
                destFile
            } catch (t: Throwable) {
                partFile.delete()
                AppLogger.log("UPDATE", "Ошибка загрузки APK: ${t.localizedMessage ?: t.javaClass.simpleName}")
                throw t
            }
        }
    }

    fun compareVersions(remote: String, local: String): Int {
        val rParts = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val max = maxOf(rParts.size, lParts.size)
        for (i in 0 until max) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }
}
