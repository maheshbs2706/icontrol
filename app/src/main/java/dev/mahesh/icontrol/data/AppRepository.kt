package dev.mahesh.icontrol.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
)

data class ManagedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val canUninstall: Boolean,
)

data class InputEntry(
    val id: String,
    val label: String,
)

/** App artwork: a 16:9 banner when the app ships one, otherwise its square icon. */
data class AppImage(val bitmap: ImageBitmap, val isBanner: Boolean)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val images = LruCache<String, AppImage>(40)
    private val icons = LruCache<String, ImageBitmap>(64)

    fun loadApps(): List<AppEntry> {
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        // Leanback entries first so TV activities win over the phone launcher activity.
        (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != context.packageName }
            .filterNot { ri -> VENDOR_NOISE.any { ri.activityInfo.packageName.startsWith(it) } }
            .forEach { byPackage.putIfAbsent(it.activityInfo.packageName, it) }

        return byPackage.values
            .map { ri ->
                AppEntry(
                    packageName = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Apps worth managing: everything with a launcher entry, everything the user installed, and everything switched
     * off (a disabled app loses its launcher entry, so it would otherwise be impossible to find and re-enable).
     */
    @Suppress("DEPRECATION")
    fun loadManagedApps(): List<ManagedApp> {
        val launchable = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .mapTo(HashSet()) { it.activityInfo.packageName }
        return pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .map { info ->
                val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                ManagedApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isSystem = system,
                    isEnabled = info.enabled,
                    // A system app can't be removed, but an update installed over it can be rolled back.
                    canUninstall = !system || info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                )
            }
            .filter { !it.isEnabled || !it.isSystem || it.packageName in launchable }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun managedIcon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                pm.getApplicationIcon(packageName)
                    .toBitmap(RING_ICON_SIZE, RING_ICON_SIZE)
                    .asImageBitmap()
                    .also { icons.put(packageName, it) }
            }.getOrNull()
        }
    }

    fun loadInputs(): List<InputEntry> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()
        return runCatching {
            manager.tvInputList
                .filter { it.isPassthroughInput }
                .map { info ->
                    val label = info.loadCustomLabel(context)?.toString()
                        ?: info.loadLabel(context)?.toString()
                        ?: info.id
                    InputEntry(info.id, label)
                }
        }.getOrDefault(emptyList())
    }

    fun launchIntent(app: AppEntry): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun inputIntent(input: InputEntry): Intent = Intent(
        Intent.ACTION_VIEW,
        TvContract.buildChannelUriForPassthroughInput(input.id),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun image(app: AppEntry): AppImage? {
        images.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val info = pm.getActivityInfo(app.component, 0)
                val banner = info.loadBanner(pm)
                val result = if (banner != null) {
                    AppImage(banner.toBitmap(BANNER_W, BANNER_H).asImageBitmap(), isBanner = true)
                } else {
                    AppImage(info.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap(), isBanner = false)
                }
                images.put(app.packageName, result)
                result
            }.getOrNull()
        }
    }

    /** Small square launcher icon, for the app ring where a 16:9 banner would not fit. */
    suspend fun icon(app: AppEntry): ImageBitmap? {
        icons.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                pm.getActivityInfo(app.component, 0).loadIcon(pm)
                    .toBitmap(RING_ICON_SIZE, RING_ICON_SIZE)
                    .asImageBitmap()
                    .also { icons.put(app.packageName, it) }
            }.getOrNull()
        }
    }

    fun evict(packageName: String) {
        images.remove(packageName)
        icons.remove(packageName)
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private companion object {
        /** Factory, diagnostics and setup activities OEMs expose as launchable; never useful on a home screen. */
        val VENDOR_NOISE = listOf(
            "com.mstar.",
            "com.mediatek.",
            "mediatek.",
            "com.oneplus.tv.factory",
            "com.oneplus.oobe",
            "com.heytap.optvfactory",
            "com.heytap.tv.remotediagnosing",
            "com.heytap.tv.remotecontrolinspect",
            "com.google.android.tungsten",
            "com.google.android.onetimeinitializer",
        )
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON_SIZE = 192
        const val RING_ICON_SIZE = 96
    }
}
