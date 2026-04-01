package com.honeyjar.app.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.honeyjar.app.data.dao.AppCategoryDao
import com.honeyjar.app.data.entities.AppCategoryEntity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an unknown package name to a HoneyJar category using two strategies:
 *
 *  1. On-device: reads ApplicationInfo.category from PackageManager (instant, no network).
 *     Android populates this field for apps installed via Google Play since API 26.
 *
 *  2. Play Store HTML scrape: fetches the app's Play Store page and reads the
 *     itemprop="applicationCategory" meta tag. One network call per unknown package,
 *     result cached permanently in Room.
 *
 * Results are kept in a fast in-process ConcurrentHashMap so the DAO is only hit
 * once per process lifetime per package.
 *
 * Usage (from a coroutine on Dispatchers.IO):
 *   val category = AppCategoryResolver.resolve(pkg, context, dao)
 */
object AppCategoryResolver {

    private const val TAG = "HoneyJar-CategoryResolver"

    // In-process cache: survives for the lifetime of the service process.
    // Seeded from Room on first hit; Room is only written when a new resolution occurs.
    private val memoryCache = ConcurrentHashMap<String, String>()

    /**
     * Returns a HoneyJar category for [packageName], or [NotificationCategories.SYSTEM]
     * if neither strategy can identify it.
     *
     * Must be called from a background coroutine (Dispatchers.IO).
     */
    suspend fun resolve(
        packageName: String,
        context: Context,
        dao: AppCategoryDao
    ): String {
        // 1. In-process memory cache (fastest — no suspend needed)
        memoryCache[packageName]?.let { return it }

        // 2. Room cache (already resolved in a previous process lifetime)
        dao.getByPackage(packageName)?.let { cached ->
            memoryCache[packageName] = cached.category
            return cached.category
        }

        // 3. On-device PackageManager — ApplicationInfo.category
        val deviceCategory = resolveFromPackageManager(packageName, context)
        if (deviceCategory != null) {
            Log.d(TAG, "PackageManager hit: $packageName → $deviceCategory")
            cache(packageName, deviceCategory, "device", dao)
            return deviceCategory
        }

        // 4. Play Store HTML scrape (network — only runs if app is in the Play Store)
        val playCategory = resolveFromPlayStore(packageName)
        if (playCategory != null) {
            Log.d(TAG, "Play Store hit: $packageName → $playCategory")
            cache(packageName, playCategory, "playstore", dao)
            return playCategory
        }

        // 5. Give up — fall back to system
        Log.d(TAG, "Unresolved: $packageName → system")
        cache(packageName, NotificationCategories.SYSTEM, "playstore", dao)
        return NotificationCategories.SYSTEM
    }

    // ── PackageManager ────────────────────────────────────────────────────────

    private fun resolveFromPackageManager(packageName: String, context: Context): String? {
        return try {
            val info = context.packageManager.getApplicationInfo(
                packageName, PackageManager.GET_META_DATA
            )
            mapApplicationInfoCategory(info.category)
        } catch (e: PackageManager.NameNotFoundException) {
            // App not installed (e.g. notification from a removed app)
            null
        } catch (e: Exception) {
            Log.w(TAG, "PackageManager error for $packageName", e)
            null
        }
    }

    /**
     * Maps Android's ApplicationInfo.category constants to HoneyJar categories.
     * Returns null for CATEGORY_UNDEFINED (-1) so the caller falls through to Play Store.
     */
    private fun mapApplicationInfoCategory(apiCategory: Int): String? {
        return when (apiCategory) {
            ApplicationInfo.CATEGORY_SOCIAL                 -> NotificationCategories.SOCIAL
            ApplicationInfo.CATEGORY_COMMUNICATION         -> NotificationCategories.MESSAGES
            ApplicationInfo.CATEGORY_NEWS                  -> NotificationCategories.MEDIA
            ApplicationInfo.CATEGORY_VIDEO                 -> NotificationCategories.MEDIA
            ApplicationInfo.CATEGORY_MUSIC                 -> NotificationCategories.MEDIA
            ApplicationInfo.CATEGORY_IMAGE                 -> NotificationCategories.MEDIA
            ApplicationInfo.CATEGORY_MAPS                  -> NotificationCategories.TRAVEL
            ApplicationInfo.CATEGORY_GAME                  -> NotificationCategories.MEDIA
            ApplicationInfo.CATEGORY_PRODUCTIVITY          -> NotificationCategories.DEVICE
            ApplicationInfo.CATEGORY_ACCESSIBILITY         -> NotificationCategories.DEVICE
            ApplicationInfo.CATEGORY_UNDEFINED             -> null  // fall through
            else                                           -> null  // future constants
        }
    }

    // ── Play Store scrape ─────────────────────────────────────────────────────

    private fun resolveFromPlayStore(packageName: String): String? {
        // Don't attempt for clearly non-Play packages (Samsung system, AOSP internals)
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("com.samsung.android.") ||
            packageName == "android") return null

        return try {
            val url = URL("https://play.google.com/store/apps/details?id=$packageName&hl=en_GB")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout    = 8_000
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")

            if (conn.responseCode != 200) {
                Log.d(TAG, "Play Store HTTP ${conn.responseCode} for $packageName")
                return null
            }

            // Read only enough of the response to find the category — it appears in the
            // first ~8KB of the page inside an itemprop="applicationCategory" attribute.
            val body = conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                var line = reader.readLine()
                var bytesRead = 0
                while (line != null && bytesRead < 16_000) {
                    sb.append(line)
                    bytesRead += line.length
                    line = reader.readLine()
                }
                sb.toString()
            }
            conn.disconnect()

            parsePlayStoreCategory(body, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Play Store fetch failed for $packageName: ${e.message}")
            null
        }
    }

    /**
     * Extracts the Google Play category string from the page HTML and maps it to
     * a HoneyJar category.
     *
     * Google Play embeds the category in a link like:
     *   /store/apps/category/COMMUNICATION
     * and also sometimes in itemprop="applicationCategory" content="...".
     * We try both patterns.
     */
    private fun parsePlayStoreCategory(html: String, packageName: String): String? {
        // Pattern 1: /store/apps/category/SOCIAL  (most reliable)
        val categoryPathRegex = Regex("""/store/apps/category/([A-Z_]+)""")
        val pathMatch = categoryPathRegex.find(html)?.groupValues?.get(1)

        // Pattern 2: itemprop="applicationCategory" content="Social"
        val itempropRegex = Regex("""itemprop="applicationCategory"[^>]*content="([^"]+)"""")
        val itempropMatch = itempropRegex.find(html)?.groupValues?.get(1)

        val raw = pathMatch ?: itempropMatch?.uppercase()?.replace(" ", "_")

        Log.d(TAG, "Play Store category for $packageName: raw=$raw (path=$pathMatch, itemprop=$itempropMatch)")

        return raw?.let { mapPlayStoreCategory(it) }
    }

    /**
     * Maps Google Play category strings to HoneyJar categories.
     * Full list: https://support.google.com/googleplay/android-developer/answer/9859673
     */
    private fun mapPlayStoreCategory(playCategory: String): String {
        return when (playCategory) {
            // Messages
            "COMMUNICATION"                       -> NotificationCategories.MESSAGES

            // Social
            "SOCIAL", "DATING"                    -> NotificationCategories.SOCIAL

            // Email (Play doesn't have a dedicated email category — falls under Communication)

            // Finance
            "FINANCE", "BUSINESS"                 -> NotificationCategories.FINANCE

            // Shopping
            "SHOPPING", "FOOD_AND_DRINK",
            "HOUSE_AND_HOME"                      -> NotificationCategories.SHOPPING

            // Travel
            "TRAVEL_AND_LOCAL", "MAPS_AND_NAVIGATION",
            "AUTO_AND_VEHICLES"                   -> NotificationCategories.TRAVEL

            // Weather
            "WEATHER"                             -> NotificationCategories.WEATHER

            // Media / Entertainment
            "ENTERTAINMENT", "MUSIC_AND_AUDIO",
            "VIDEO_PLAYERS", "PHOTOGRAPHY",
            "COMICS", "BOOKS_AND_REFERENCE",
            "NEWS_AND_MAGAZINES", "SPORTS",
            "ART_AND_DESIGN"                      -> NotificationCategories.MEDIA

            // Games — all subcategories contain "GAME"
            else if (playCategory.startsWith("GAME")) -> NotificationCategories.MEDIA

            // Health
            "HEALTH_AND_FITNESS",
            "MEDICAL"                             -> NotificationCategories.DEVICE  // no health category yet

            // Device / productivity
            "TOOLS", "PRODUCTIVITY",
            "PERSONALIZATION", "LIFESTYLE",
            "LIBRARIES_AND_DEMO",
            "EDUCATION", "EDUCATIONAL"            -> NotificationCategories.DEVICE

            // Security / device
            "SECURITY"                            -> NotificationCategories.DEVICE

            // Everything else → system
            else                                  -> NotificationCategories.SYSTEM
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun cache(
        packageName: String,
        category: String,
        source: String,
        dao: AppCategoryDao
    ) {
        memoryCache[packageName] = category
        dao.insert(AppCategoryEntity(packageName, category, source))
    }

    /** Evict Play Store entries older than 30 days so they get refreshed. */
    suspend fun evictStale(dao: AppCategoryDao) {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.evictStalePlayStore(thirtyDaysAgo)
    }

    /** Pre-warm the in-process cache from Room (call once at service start). */
    suspend fun prewarm(dao: AppCategoryDao) {
        // Nothing to do here — Room is read lazily on first miss.
        // This method exists as a hook if bulk pre-loading is ever needed.
        evictStale(dao)
    }
}
