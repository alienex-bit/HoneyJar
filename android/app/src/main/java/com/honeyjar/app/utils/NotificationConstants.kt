package com.honeyjar.app.utils

object NotificationCategories {
    const val URGENT    = "urgent"
    const val MESSAGES  = "messages"
    const val SOCIAL    = "social"
    const val EMAIL     = "email"
    const val CALENDAR  = "calendar"
    const val CALLS     = "calls"
    const val WEATHER   = "weather"
    const val TRAVEL    = "travel"
    const val FINANCE   = "finance"
    const val SHOPPING  = "shopping"
    const val MEDIA     = "media"
    // Device sub-categories (replaces the single DEVICE bucket)
    const val SECURITY  = "security"   // AdGuard, VPN, device security scans
    const val CONNECTED = "connected"  // Link to Windows, Watch, SmartThings, router
    const val UPDATES   = "updates"    // Play Store, Galaxy Store, app/OS updates
    const val PHOTOS    = "photos"     // Camera, Google Photos, Samsung Cloud backup
    const val SYSTEM    = "system"     // OS noise: SystemUI, charging, alarms, downloads
    // Keep DEVICE as a fallback for unknown device-ish packages from the dynamic resolver
    const val DEVICE    = "device"
    // Legacy — disabled in DB but kept so old notification rows don't become orphaned
    const val DELIVERY  = "delivery"
}
