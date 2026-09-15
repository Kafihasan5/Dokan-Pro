package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnection())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var onNetworkRestoredCallback: (() -> Unit)? = null

    fun setOnNetworkRestoredCallback(callback: () -> Unit) {
        onNetworkRestoredCallback = callback
    }

    init {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Network became available, check if validated
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: true
                        val wasOffline = !_isOnline.value
                        _isOnline.value = isValidated
                        if (wasOffline && isValidated) {
                            onNetworkRestoredCallback?.invoke()
                        }
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        val online = hasInternet && isValidated
                        val wasOffline = !_isOnline.value
                        _isOnline.value = online
                        if (wasOffline && online) {
                            onNetworkRestoredCallback?.invoke()
                        }
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = false
                    }
                }
            )
        } catch (_: Exception) {
            // Fallback gracefully if permission or service unavailable
        }
    }

    fun isCurrentlyOnline(): Boolean {
        return _isOnline.value
    }

    fun markOffline() {
        _isOnline.value = false
    }

    private fun checkInitialConnection(): Boolean {
        return try {
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        } catch (_: Exception) {
            false
        }
    }
}
