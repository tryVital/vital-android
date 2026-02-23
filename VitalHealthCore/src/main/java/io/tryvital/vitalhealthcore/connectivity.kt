package io.tryvital.vitalhealthcore

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

val Context.isConnectedToInternet: Boolean
    @SuppressLint("MissingPermission") get() {
        val connectivityManager = this.getSystemService(ConnectivityManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager
                .getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.let { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
                ?: false
        } else {
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting ?: false
        }
    }
