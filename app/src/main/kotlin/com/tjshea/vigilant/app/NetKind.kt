package com.tjshea.vigilant.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Which network the phone is on, as the connection figures name it ("Wi-Fi", "mobile", "other"); null when it can't be read ([com.tjshea.vigilant.data.diag.NetInterceptor]). */
object NetKind {
    fun of(context: Context): String? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "other"
        }
    }.getOrNull()
}
