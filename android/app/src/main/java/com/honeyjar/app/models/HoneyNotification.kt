package com.honeyjar.app.models

data class HoneyNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val priority: String,
    val isResolved: Boolean = false,
    val isGrouped: Boolean = false,
    val snoozeUntil: Long = 0,
    val resolvedAt: Long = 0,
    val systemActions: List<String> = emptyList()
)
