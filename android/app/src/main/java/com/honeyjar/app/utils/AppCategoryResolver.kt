package com.honeyjar.app.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.honeyjar.app.data.dao.AppCategoryDao
import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.entities.AppCategoryEntity
import com.honeyjar.app.data.entities.PriorityGroupEntity
import com.honeyjar.app.repositories.PriorityRepository
import com.honeyjar.app.utils.NotificationCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an unknown package name to a HoneyJar category using three strategies:
 *
 *  1. LOOKUP TABLE: Quick match for popular apps.
 *  2. SUBSTRING MATCH: Checks package name and text for keywords.
 *  3. PLAY STORE SCRAPE: Fetches category from Google Play URL if network available.
 *
 * Results are cached in Room to avoid redundant work.
 */
object AppCategoryResolver {

    private const val TAG = "HoneyJar-CategoryResolver"

    val PACKAGE_CATEGORY_MAP: Map<String, String> = mapOf(
        "com.whatsapp"                              to NotificationCategories.MESSAGES,
        "com.whatsapp.w4b"                         to NotificationCategories.MESSAGES,
        "org.telegram.messenger"                   to NotificationCategories.MESSAGES,
        "tw.nekomimi.nekogram"                     to NotificationCategories.MESSAGES,
        "org.thoughtcrime.securesms"               to NotificationCategories.MESSAGES,
        "com.google.android.apps.messaging"        to NotificationCategories.MESSAGES,
        "com.samsung.android.messaging"            to NotificationCategories.MESSAGES,

        "com.twitter.android"                      to NotificationCategories.SOCIAL,
        "com.facebook.orca"                        to NotificationCategories.SOCIAL,
        "com.facebook.katana"                      to NotificationCategories.SOCIAL,
        "com.instagram.android"                    to NotificationCategories.SOCIAL,
        "com.instagram.barcelona"                  to NotificationCategories.SOCIAL,
        "com.reddit.frontpage"                     to NotificationCategories.SOCIAL,
        "com.linkedin.android"                     to NotificationCategories.SOCIAL,
        "com.discord"                              to NotificationCategories.SOCIAL,
        "com.zhiliaoapp.musically"                 to NotificationCategories.SOCIAL,
        "com.spond.spond"                          to NotificationCategories.SOCIAL,

        "com.hb.dialer.free"                       to NotificationCategories.CALLS,
        "com.samsung.android.dialer"               to NotificationCategories.CALLS,
        "com.google.android.dialer"                to NotificationCategories.CALLS,
        "com.qohlo.ca"                             to NotificationCategories.CALLS,

        "com.google.android.gm"                    to NotificationCategories.EMAIL,
        "com.microsoft.office.outlook"             to NotificationCategories.EMAIL,
        "com.easilydo.mail"                        to NotificationCategories.EMAIL,
        "com.samsung.android.email.provider"       to NotificationCategories.EMAIL,

        "com.samsung.android.calendar"             to NotificationCategories.CALENDAR,
        "com.google.android.calendar"              to NotificationCategories.CALENDAR,
        "com.appgenix.bizcal.pro"                  to NotificationCategories.CALENDAR,

        "com.revolut.revolut"                      to NotificationCategories.FINANCE,
        "io.safepal.wallet"                        to NotificationCategories.FINANCE,
        "com.google.android.apps.walletnfcrel"     to NotificationCategories.FINANCE,
        "com.clearscore.mobile"                    to NotificationCategories.FINANCE,
        "com.fumbgames.bitcoinminor"               to NotificationCategories.FINANCE,
        "io.voodoo.paper2"                         to NotificationCategories.FINANCE,
        "com.blockchainvault"                      to NotificationCategories.FINANCE,
        "com.zypto"                                to NotificationCategories.FINANCE,

        "com.amazon.mShop.android.shopping"        to NotificationCategories.SHOPPING,
        "com.amazon.dee.app"                       to NotificationCategories.SHOPPING,
        "com.amazon.avod.thirdpartyclient"         to NotificationCategories.SHOPPING,
        "uk.co.next.android"                       to NotificationCategories.SHOPPING,
        "com.asda.rewards"                         to NotificationCategories.SHOPPING,
        "com.wayfair.wayfair"                      to NotificationCategories.SHOPPING,
        "uk.co.dominos.android"                    to NotificationCategories.SHOPPING,
        "uk.co.dreamcargiveaways"                  to NotificationCategories.SHOPPING,
        "com.bluelightcard.user"                   to NotificationCategories.SHOPPING,
        "tv.telescope.onepercentclub.uk"           to NotificationCategories.SHOPPING,
        "com.pal.train"                            to NotificationCategories.SHOPPING,
        "net.tsapps.appsales"                      to NotificationCategories.SHOPPING,

        "com.ubercab"                              to NotificationCategories.TRAVEL,
        "com.ubercab.eats"                         to NotificationCategories.TRAVEL,
        "com.waze"                                 to NotificationCategories.TRAVEL,
        "com.google.android.apps.maps"             to NotificationCategories.TRAVEL,
        "com.google.android.projection.gearhead"   to NotificationCategories.TRAVEL,
        "com.wetherspoon.orderandpay"              to NotificationCategories.TRAVEL,

        "com.devexpert.weather"                    to NotificationCategories.WEATHER,
        "com.windyty.android"                      to NotificationCategories.WEATHER,
        "com.jrustonapps.mylightningtrackerpro"    to NotificationCategories.WEATHER,
        "com.accuweather.android"                  to NotificationCategories.WEATHER,
        "com.sec.android.daemonapp"                to NotificationCategories.WEATHER,

        "app.revanced.android.youtube"             to NotificationCategories.MEDIA,
        "com.google.android.youtube"               to NotificationCategories.MEDIA,
        "com.google.android.apps.youtube.music"    to NotificationCategories.MEDIA,
        "app.rvx.android.apps.youtube.music"       to NotificationCategories.MEDIA,
        "com.spotify.music"                        to NotificationCategories.MEDIA,
        "com.google.android.apps.magazines"        to NotificationCategories.MEDIA,
        "com.lemon.lvoverseas"                     to NotificationCategories.MEDIA,
        "com.mixcloud.player"                      to NotificationCategories.MEDIA,
        "com.patreon.android"                      to NotificationCategories.MEDIA,
        "com.moonactive.jellybusters"              to NotificationCategories.MEDIA,
        "com.hyperup.holepeople"                   to NotificationCategories.MEDIA,
        "com.pocketchamps.game"                    to NotificationCategories.MEDIA,
        "com.pc.sand.loop"                         to NotificationCategories.MEDIA,
        "com.funcamerastudio.videomaker"           to NotificationCategories.MEDIA,
        "com.backdrops.wallpapers"                 to NotificationCategories.MEDIA,
        "com.adobe.reader"                         to NotificationCategories.MEDIA,
        "com.samsung.storyservice"                 to NotificationCategories.MEDIA,
        "com.appmind.radios.gb"                    to NotificationCategories.MEDIA,

        // ── Security ─────────────────────────────────────────────────────────
        "com.adguard.android"                      to NotificationCategories.SECURITY,
        "com.surfshark.vpnclient.android"          to NotificationCategories.SECURITY,
        "com.samsung.android.sm.devicesecurity"    to NotificationCategories.SECURITY,

        // ── Connected ─────────────────────────────────────────────────────────
        "com.microsoft.appmanager"                 to NotificationCategories.CONNECTED, // Link to Windows
        "com.samsung.wearable.watch6plugin"        to NotificationCategories.CONNECTED, // Galaxy Watch
        "com.samsung.android.oneconnect"           to NotificationCategories.CONNECTED, // SmartThings
        "com.eero.android"                         to NotificationCategories.CONNECTED, // router

        // ── Updates ───────────────────────────────────────────────────────────
        "com.wssyncmldm"                           to NotificationCategories.UPDATES,   // Samsung FOTA
        "com.android.vending"                      to NotificationCategories.UPDATES,   // Play Store
        "com.sec.android.app.samsungapps"          to NotificationCategories.UPDATES,   // Galaxy Store
        "org.mozilla.firefox"                      to NotificationCategories.UPDATES,
        "com.google.android.packageinstaller"      to NotificationCategories.UPDATES,
        "xda.dante.shm.mod.companion"              to NotificationCategories.UPDATES,

        // ── Photos ────────────────────────────────────────────────────────────
        "com.sec.android.app.camera"               to NotificationCategories.PHOTOS,
        "com.google.android.apps.photos"           to NotificationCategories.PHOTOS,
        "com.samsung.android.scloud"               to NotificationCategories.PHOTOS,    // Samsung Cloud
        "com.samsung.android.app.smartcapture"     to NotificationCategories.PHOTOS,    // Screenshot
        "com.microsoft.skydrive"                   to NotificationCategories.PHOTOS,    // OneDrive

        // ── System (OS noise) ─────────────────────────────────────────────────
        "com.android.systemui"                     to NotificationCategories.SYSTEM,
        "android"                                  to NotificationCategories.SYSTEM,
        "com.google.android.gms"                   to NotificationCategories.SYSTEM,
        "com.android.providers.downloads"          to NotificationCategories.SYSTEM,
        "com.samsung.android.bixby.wakeup"         to NotificationCategories.SYSTEM,
        "com.samsung.android.voc"                  to NotificationCategories.SYSTEM,
        "com.samsung.android.forest"               to NotificationCategories.SYSTEM,
        "com.sec.android.app.clockpackage"         to NotificationCategories.SYSTEM,
        "com.google.android.googlequicksearchbox"  to NotificationCategories.SYSTEM,
        "com.sec.android.app.shealth"              to NotificationCategories.SYSTEM,
        "org.zwanoo.android.speedtest"             to NotificationCategories.SYSTEM,
    )

    private val memoryCache = ConcurrentHashMap<String, String>()

    fun categorizeStatic(pkg: String, title: String = "", text: String = ""): String? {
        val p = pkg.lowercase()
        val t = title.lowercase()
        val b = text.lowercase()

        PACKAGE_CATEGORY_MAP[pkg]?.let { return it }

        return when {
            t.contains("critical") || t.contains("security alert") || t.contains("fraud") ||
            t.contains("unauthorised") || t.contains("unauthorized") ||
            b.contains("critical") || b.contains("security alert") -> NotificationCategories.URGENT

            p.contains("whatsapp") || p.contains("telegram") || p.contains("signal") ||
            p.contains(".sms") || p.contains(".mms") || p.contains(".messaging") ||
            p.contains(".messages") -> NotificationCategories.MESSAGES

            p.contains("twitter") || p.contains("instagram") || p.contains("facebook") ||
            p.contains("reddit") || p.contains("linkedin") || p.contains("discord") ||
            p.contains("tiktok") || p.contains("snapchat") || p.contains("pinterest") ||
            p.contains("threads") || p.contains("mastodon") -> NotificationCategories.SOCIAL

            p.contains("gmail") || p.contains("outlook") || p.contains(".mail") ||
            p.contains("mailbox") || p.contains("easilydo") -> NotificationCategories.EMAIL

            p.contains("calendar") || p.contains("bizcal") ||
            t.contains("appointment") || t.contains("meeting") ||
            t.contains("reminder") -> NotificationCategories.CALENDAR

            p.contains("revolut") || p.contains("monzo") || p.contains("starling") ||
            p.contains("barclays") || p.contains("lloyds") || p.contains("hsbc") ||
            p.contains("paypal") || p.contains("cashapp") || p.contains("wallet") ||
            p.contains("safepal") || p.contains("coinbase") || p.contains("binance") ||
            p.contains("clearscore") || p.contains("experian") ||
            t.contains("payment") || t.contains("transaction") || t.contains("transfer") -> NotificationCategories.FINANCE

            p.contains("amazon") || p.contains("ebay") || p.contains("etsy") ||
            p.contains("wayfair") || p.contains("asos") || p.contains("next.android") ||
            p.contains("asda") || p.contains("tesco") || p.contains("dominos") ||
            p.contains("deliveroo") || p.contains("justeat") || p.contains("trainline") ||
            p.contains("pal.train") || p.contains("bluelightcard") || p.contains("dreamcar") ||
            p.contains("telescope") || p.contains("giveaway") ||
            t.contains("delivery") || t.contains("dispatched") || t.contains("your order") ||
            b.contains("parcel") || b.contains("tracking") -> NotificationCategories.SHOPPING

            p.contains("uber") || p.contains("lyft") || p.contains("bolt.") ||
            p.contains("waze") || p.contains("maps") || p.contains("citymapper") ||
            p.contains("trainpal") || p.contains("gearhead") || p.contains("autoproject") -> NotificationCategories.TRAVEL

            p.contains("weather") || p.contains("windy") || p.contains("lightning") ||
            p.contains("accuweather") || p.contains("bbc.mobile.weather") ||
            p.contains("daemonapp") -> NotificationCategories.WEATHER

            p.contains("youtube") || p.contains("spotify") || p.contains("netflix") ||
            p.contains("disney") || p.contains("primevideo") || p.contains("itvhub") ||
            p.contains("bbciplayer") || p.contains("soundcloud") || p.contains("mixcloud") ||
            p.contains("audible") || p.contains("capcut") || p.contains("lvoverseas") ||
            p.contains("magazines") || p.contains("patreon") ||
            p.contains("game") || p.contains("games") || p.contains("gaming") -> NotificationCategories.MEDIA

            p.contains("adguard") || p.contains("surfshark") || p.contains("vpnclient") ||
            p.contains("devicesecurity") || p.contains("kaspersky") || p.contains("avast") ||
            p.contains("bitdefender") -> NotificationCategories.SECURITY

            p.contains("appmanager") || p.contains("oneconnect") || p.contains("smartthings") ||
            p.contains("eero") || p.contains("wearable") || p.contains("watchplugin") -> NotificationCategories.CONNECTED

            p.contains("vending") || p.contains("samsungapps") || p.contains("packageinstaller") ||
            p.contains("wssyncmldm") || p.contains("fota") || p.contains("firefox") ||
            p.contains("chrome") || p.contains("browser") -> NotificationCategories.UPDATES

            p.contains("camera") || p.contains("photos") || p.contains("gallery") ||
            p.contains("scloud") || p.contains("smartcapture") || p.contains("skydrive") ||
            p.contains("onedrive") -> NotificationCategories.PHOTOS

            p.contains("systemui") || p.contains("providers.downloads") ||
            p.contains("bixby") || p.contains("gms") || p == "android" -> NotificationCategories.SYSTEM

            else -> null
        }
    }

    suspend fun resolve(packageName: String, @Suppress("UNUSED_PARAMETER") context: Context, dao: AppCategoryDao): String {
        memoryCache[packageName]?.let { return it }

        val dbEntry = withContext(Dispatchers.IO) { dao.getByPackage(packageName) }
        dbEntry?.let {
            memoryCache[packageName] = it.category
            return it.category
        }

        val staticCategory = categorizeStatic(packageName)
        if (staticCategory != null) {
            cache(packageName, staticCategory, "manual", dao)
            return staticCategory
        }

        val playCategory = resolveFromPlayStore(packageName)
        if (playCategory != null) {
            cache(packageName, playCategory, "playstore", dao)
            return playCategory
        }

        cache(packageName, NotificationCategories.SYSTEM, "none", dao)
        return NotificationCategories.SYSTEM
    }

    private suspend fun resolveFromPlayStore(packageName: String): String? = withContext(Dispatchers.IO) {
        if (packageName.startsWith("com.android.") || packageName.startsWith("com.samsung.") || packageName == "android") return@withContext null
        try {
            val url = URL("https://play.google.com/store/apps/details?id=$packageName&hl=en_GB")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            
            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val categoryPathRegex = Regex("""/store/apps/category/([A-Z_]+)""")
            val raw = categoryPathRegex.find(body)?.groupValues?.get(1)
            
            raw?.let { mapPlayStoreCategory(it) }
        } catch (_: Exception) { null }
    }

    private fun mapPlayStoreCategory(playCategory: String): String {
        return when (playCategory) {
            "COMMUNICATION" -> NotificationCategories.MESSAGES
            "SOCIAL", "DATING" -> NotificationCategories.SOCIAL
            "FINANCE", "BUSINESS" -> NotificationCategories.FINANCE
            "SHOPPING", "FOOD_AND_DRINK", "HOUSE_AND_HOME" -> NotificationCategories.SHOPPING
            "TRAVEL_AND_LOCAL", "MAPS_AND_NAVIGATION", "AUTO_AND_VEHICLES" -> NotificationCategories.TRAVEL
            "WEATHER" -> NotificationCategories.WEATHER
            "ENTERTAINMENT", "MUSIC_AND_AUDIO", "VIDEO_PLAYERS", "PHOTOGRAPHY", "COMICS", "BOOKS_AND_REFERENCE", "NEWS_AND_MAGAZINES", "SPORTS", "ART_AND_DESIGN" -> NotificationCategories.MEDIA
            else -> if (playCategory.startsWith("GAME")) NotificationCategories.MEDIA else NotificationCategories.SYSTEM
        }
    }

    private suspend fun cache(packageName: String, category: String, source: String, dao: AppCategoryDao) {
        memoryCache[packageName] = category
        withContext(Dispatchers.IO) {
            dao.insert(AppCategoryEntity(packageName, category, source))
        }
    }

    suspend fun prewarm(dao: AppCategoryDao) {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        withContext(Dispatchers.IO) {
            dao.evictStalePlayStore(thirtyDaysAgo)
        }
    }

    /**
     * Maintenance task: ensures all system categories exist in the priority_groups table
     * so they show up in HeroCard and History even if no notifications exist yet.
     */
    suspend fun ensureCategoriesExist(repo: PriorityRepository) {
        val existing = repo.allPriorityGroups.first().map { group -> group.key }
        val systems = listOf(
            NotificationCategories.URGENT   to ("#ef4444" to 0),
            NotificationCategories.MESSAGES to ("#3b82f6" to 1),
            NotificationCategories.SOCIAL   to ("#ec4899" to 2),
            NotificationCategories.EMAIL    to ("#a855f7" to 3),
            NotificationCategories.CALENDAR to ("#f59e0b" to 4),
            NotificationCategories.CALLS    to ("#10b981" to 5),
            NotificationCategories.FINANCE  to ("#84cc16" to 6),
            NotificationCategories.TRAVEL   to ("#f97316" to 7),
            NotificationCategories.SHOPPING to ("#f43f5e" to 8),
            NotificationCategories.WEATHER  to ("#38bdf8" to 9),
            NotificationCategories.MEDIA    to ("#8b5cf6" to 10),
            NotificationCategories.DEVICE   to ("#64748b" to 11),
            NotificationCategories.SYSTEM   to ("#94a3b8" to 12)
        )
        
        systems.forEach { (key, pair) ->
            if (!existing.contains(key)) {
                repo.insert(PriorityGroupEntity(
                    key = key,
                    label = key.replaceFirstChar { it.uppercase() },
                    colour = pair.first,
                    position = pair.second
                ))
            }
        }
    }
}
