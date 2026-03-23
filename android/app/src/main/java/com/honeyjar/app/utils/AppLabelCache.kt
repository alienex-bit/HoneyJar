package com.honeyjar.app.utils

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object AppLabelCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(packageName: String, context: Context): String {
        return cache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName.split(".").last().replaceFirstChar { it.uppercase() }
            }
        }
    }
}
