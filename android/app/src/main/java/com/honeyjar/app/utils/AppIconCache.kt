package com.honeyjar.app.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val failures = ConcurrentHashMap.newKeySet<String>()

    /** Returns a cached icon immediately without loading — returns null if not yet in cache. */
    fun peek(packageName: String): ImageBitmap? = cache[packageName]

    fun get(packageName: String, context: Context): ImageBitmap? {
        if (cache.size > 300) cache.clear()
        if (failures.size > 300) failures.clear()
        cache[packageName]?.let { return it }
        if (failures.contains(packageName)) return null

        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            val image = bitmap.asImageBitmap()
            cache[packageName] = image
            image
        } catch (e: Exception) {
            failures.add(packageName)
            null
        }
    }
}
