package com.honeyjar.app.data.entities

import androidx.room.*
import java.util.Arrays

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val priority: String,
    val isResolved: Boolean = false,
    val isGrouped: Boolean = false,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val iv: ByteArray? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val encryptedData: ByteArray? = null,
    val snoozeUntil: Long = 0,
    val resolvedAt: Long = 0,
    val systemActionsJson: String? = null,
    val isDismissedByUser: Boolean = false,
    val dismissedAt: Long = 0L,
    val alertFiredAt: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationEntity) return false
        return id == other.id &&
            packageName == other.packageName &&
            title == other.title &&
            text == other.text &&
            postTime == other.postTime &&
            priority == other.priority &&
            isResolved == other.isResolved &&
            isGrouped == other.isGrouped &&
            Arrays.equals(iv, other.iv) &&
            Arrays.equals(encryptedData, other.encryptedData) &&
            snoozeUntil == other.snoozeUntil &&
            resolvedAt == other.resolvedAt &&
            systemActionsJson == other.systemActionsJson &&
            isDismissedByUser == other.isDismissedByUser &&
            dismissedAt == other.dismissedAt &&
            alertFiredAt == other.alertFiredAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + postTime.hashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + isResolved.hashCode()
        result = 31 * result + isGrouped.hashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + (encryptedData?.contentHashCode() ?: 0)
        result = 31 * result + snoozeUntil.hashCode()
        result = 31 * result + resolvedAt.hashCode()
        result = 31 * result + (systemActionsJson?.hashCode() ?: 0)
        result = 31 * result + isDismissedByUser.hashCode()
        result = 31 * result + dismissedAt.hashCode()
        result = 31 * result + alertFiredAt.hashCode()
        return result
    }
}
