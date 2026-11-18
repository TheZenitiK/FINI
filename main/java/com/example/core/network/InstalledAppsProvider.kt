package com.example.core.network

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InstalledAppsProvider {

    private val iconCache = LruCache<String, Drawable>(120)

    /**
     * Combines launcher activities with installed applications visible under the manifest
     * <queries> declarations (avoiding QUERY_ALL_PACKAGES) so both launcher apps and
     * system components (downloads, telecom, webview, etc.) appear when "Системные" is enabled.
     */
    suspend fun getLaunchableApps(context: Context): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val byPackage = LinkedHashMap<String, InstalledAppInfo>()

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = runCatching {
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())

            resolveInfos.forEach { info ->
                val activityInfo = info.activityInfo ?: return@forEach
                val pkg = activityInfo.packageName ?: return@forEach
                if (pkg == context.packageName) return@forEach
                val flags = activityInfo.applicationInfo?.flags ?: 0
                val isSystem = (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                byPackage.putIfAbsent(
                    pkg,
                    InstalledAppInfo(
                        packageName = pkg,
                        appName = label,
                        isSystemApp = isSystem
                    )
                )
            }

            val installedApps = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())

            installedApps.forEach { appInfo ->
                val pkg = appInfo.packageName ?: return@forEach
                if (pkg == context.packageName) return@forEach
                if (byPackage.containsKey(pkg)) return@forEach
                val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                val label = runCatching {
                    pm.getApplicationLabel(appInfo)?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
                byPackage[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSystem
                )
            }

            byPackage.values
                .sortedBy { it.appName.lowercase() }
                .toList()
        }

    fun getCachedAppIcon(packageName: String): Drawable? = iconCache.get(packageName)

    fun loadAppIcon(context: Context, packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()?.also { iconCache.put(packageName, it) }
    }

    fun clearIconCache() {
        iconCache.evictAll()
    }
}
