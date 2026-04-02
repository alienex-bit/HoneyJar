package com.honeyjar.app.repositories

import com.honeyjar.app.data.dao.NotificationDao
import com.honeyjar.app.data.dao.StatsDao
import com.honeyjar.app.data.entities.NotificationEntity
import com.honeyjar.app.models.HoneyNotification
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.honeyjar.app.utils.AppCategoryResolver
import com.honeyjar.app.utils.NotificationCategories
import com.honeyjar.app.utils.TimeUtils
import org.json.JSONArray
import java.util.Calendar

import com.honeyjar.app.data.HoneyEncryptor
import org.json.JSONObject

import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

object NotificationRepository {
    private val daoFlow = MutableStateFlow<NotificationDao?>(null)
    @Volatile private var dao: NotificationDao? = null
    @Volatile private var statsDao: StatsDao? = null
    @Volatile private var encryptor: HoneyEncryptor? = null
    
    private var repositoryJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.IO + repositoryJob)

    fun initialize(notificationDao: NotificationDao, statsDao: StatsDao) {
        // If the repository was previously shut down, recreate the scope
        if (!repositoryJob.isActive) {
            repositoryJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.IO + repositoryJob)
        }
        
        this.dao = notificationDao
        this.statsDao = statsDao
        this.encryptor = HoneyEncryptor()
        this.daoFlow.value = notificationDao
    }

    /**
     * Explicitly cancels all pending coroutines. Should be called when the 
     * main owner (NotificationService) is destroyed.
     */
    fun shutdown() {
        repositoryJob.cancel()
        Log.i("HoneyJar-Repo", "NotificationRepository scope has been shut down.")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications: Flow<List<HoneyNotification>> = daoFlow.flatMapLatest { currentDao ->
        currentDao?.getAllNotifications()?.map { entities ->
            entities.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notificationsHome: Flow<List<HoneyNotification>> = daoFlow.flatMapLatest { currentDao ->
        val todayStart = TimeUtils.getDayStart(System.currentTimeMillis())
        currentDao?.getHomeNotifications(todayStart)?.map { entities ->
            entities.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    fun addNotification(notification: HoneyNotification) {
        scope.launch {
            try {
                val currentDao = dao
                if (currentDao == null) {
                    Log.e("HoneyJar-Alerts", "NotificationDao is NULL! Initialization failed?")
                    return@launch
                }
                currentDao.insertNotification(notification.toEntity())
                incrementStats(notification.priority, notification.postTime)
                Log.d("HoneyJar-Alerts", "Successfully stored (encrypted) notification: ${notification.title}")
            } catch (e: Exception) {
                Log.e("HoneyJar-Alerts", "CRITICAL FAILURE in addNotification: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun incrementStats(priority: String, timestamp: Long) {
        val dayStart = TimeUtils.getDayStart(timestamp)
        val bucket = getHourBucket(timestamp)
        statsDao?.let { it.upsertStat(dayStart, priority, bucket) }
    }

    private fun getHourBucket(timestamp: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return cal.get(Calendar.HOUR_OF_DAY) / 3
    }

    fun resolveNotification(id: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao?.updateResolvedStatus(id, true, now)
        }
    }

    fun resolveGroupNotifications(ids: List<String>) {
        scope.launch {
            val now = System.currentTimeMillis()
            dao?.runInTransaction {
                ids.forEach { id ->
                    dao?.updateResolvedStatusSync(id, true, now)
                }
            }
        }
    }

    fun unresolveNotification(id: String) {
        scope.launch {
            dao?.updateResolvedStatus(id, false, 0L)
        }
    }

    fun snoozeNotification(id: String, durationMillis: Long) {
        scope.launch {
            val snoozeUntil = System.currentTimeMillis() + durationMillis
            dao?.snoozeNotification(id, snoozeUntil)
        }
    }

    fun unsnoozeNotification(id: String) {
        scope.launch {
            dao?.snoozeNotification(id, 0L)
        }
    }

    fun changePriority(id: String, newPriority: String) {
        scope.launch {
            dao?.updatePriority(id, newPriority.lowercase())
        }
    }

    fun deleteNotification(id: String) {
        scope.launch {
            dao?.deleteNotificationById(id)
        }
    }

    fun clearAllNotifications() {
        scope.launch {
            dao?.deleteAllNotifications()
        }
    }

    /**
     * Re-runs the static categoriser over every notification in the database and
     * updates any whose priority has changed. Only touches rows where the category
     * would actually change — so it's safe to run multiple times.
     *
     * Uses AppCategoryResolver.categorizeStatic() which is pure/CPU-only (no network,
     * no PackageManager) so it's fast enough to run over thousands of rows.
     *
     * Returns a pair of (total rows examined, rows updated).
     */
    suspend fun recategorizeAll(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val currentDao = dao ?: return@withContext 0 to 0
        val all = currentDao.getAllNotificationsOnce()
        var updated = 0
        // Single transaction: all 15k+ updates commit at once instead of one-by-one.
        // This is ~50x faster and avoids hammering the main thread.
        currentDao.runInTransaction {
            all.forEach { entity ->
                // Decrypt for categorization logic
                val decryptedModel = entity.toModel()
                val newCat = AppCategoryResolver.categorizeStatic(
                    decryptedModel.packageName, decryptedModel.title, decryptedModel.text
                ) ?: NotificationCategories.SYSTEM
                if (newCat != entity.priority) {
                    // updatePrioritySync is a non-suspend @Query used inside transactions
                    currentDao.updatePrioritySync(entity.id, newCat)
                    updated++
                }
            }
        }
        android.util.Log.i("HoneyJar-Recategorize", "Examined ${all.size}, updated $updated")
        all.size to updated
    }

    fun resolveAllNotifications() {
        scope.launch {
            dao?.resolveAllNotifications(System.currentTimeMillis())
        }
    }


    private fun NotificationEntity.toModel(): HoneyNotification {
        var finalTitle = title
        var finalText = text

        // Automatic Decryption if columns are populated
        if (iv != null && encryptedData != null) {
            try {
                encryptor?.let { engine ->
                    val decrypted = String(engine.decrypt(iv, encryptedData))
                    val json = JSONObject(decrypted)
                    finalTitle = json.optString("title", title)
                    finalText = json.optString("text", text)
                }
            } catch (e: Exception) {
                Log.e("HoneyJar-Security", "Decryption failed for $id, using placeholder text.")
                // Leave finalTitle/finalText as the placeholder strings stored in the DB
            }
        }

        return HoneyNotification(
            id = id,
            packageName = packageName,
            title = finalTitle,
            text = finalText,
            postTime = postTime,
            priority = priority,
            isResolved = isResolved,
            isGrouped = isGrouped,
            snoozeUntil = snoozeUntil,
            resolvedAt = resolvedAt,
            systemActions = systemActionsJson?.let { json ->
                try {
                    val array = JSONArray(json)
                    List(array.length()) { array.getString(it) }
                } catch (e: Exception) { emptyList() }
            } ?: emptyList()
        )
    }

    private fun HoneyNotification.toEntity(): NotificationEntity {
        var ivBlob: ByteArray? = null
        var dataBlob: ByteArray? = null

        try {
            encryptor?.let { engine ->
                val json = JSONObject()
                json.put("title", title)
                json.put("text", text)
                val (iv, encrypted) = engine.encrypt(json.toString().toByteArray())
                ivBlob = iv
                dataBlob = encrypted
            }
        } catch (e: Exception) {
            Log.e("HoneyJar-Security", "Encryption failed for $id, falling back to plaintext.")
        }

        return NotificationEntity(
            id = id,
            packageName = packageName,
            title = if (dataBlob != null) "[Encrypted]" else title,
            text = if (dataBlob != null) "[Encrypted]" else text,
            postTime = postTime,
            priority = priority,
            isResolved = isResolved,
            isGrouped = isGrouped,
            iv = ivBlob,
            encryptedData = dataBlob,
            snoozeUntil = snoozeUntil,
            resolvedAt = resolvedAt,
            systemActionsJson = if (systemActions.isNotEmpty()) JSONArray(systemActions).toString() else null
        )
    }
}
