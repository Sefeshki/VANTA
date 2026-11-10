package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class NetworkMonitor(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val _isTelegramReachable = MutableStateFlow(false)
    val isTelegramReachable: StateFlow<Boolean> = _isTelegramReachable.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reachabilityJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isInternetAvailable.value = true
            EventLogger.log(LogCategory.NETWORK, "Internet connection detected", LogLevel.SUCCESS)
            checkTelegramReachability()
        }

        override fun onLost(network: Network) {
            _isInternetAvailable.value = checkCurrentInternet()
            if (!_isInternetAvailable.value) {
                _isTelegramReachable.value = false
                EventLogger.log(LogCategory.NETWORK, "Internet connection lost", LogLevel.WARNING)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isInternetAvailable.value = hasInternet
            if (hasInternet && !_isTelegramReachable.value) {
                checkTelegramReachability()
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            _isInternetAvailable.value = checkCurrentInternet()
            if (_isInternetAvailable.value) {
                checkTelegramReachability()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Periodic Telegram probe (every 30 seconds when internet is up)
        scope.launch {
            while (isActive) {
                delay(30000)
                if (_isInternetAvailable.value) {
                    testTelegramConnection()
                }
            }
        }
    }

    private fun checkCurrentInternet(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkTelegramReachability() {
        reachabilityJob?.cancel()
        reachabilityJob = scope.launch {
            testTelegramConnection()
        }
    }

    private fun testTelegramConnection() {
        try {
            // Direct socket check to api.telegram.org:443
            Socket().use { socket ->
                socket.connect(InetSocketAddress("api.telegram.org", 443), 4000)
                val wasReachable = _isTelegramReachable.value
                _isTelegramReachable.value = true
                if (!wasReachable) {
                    EventLogger.log(LogCategory.TELEGRAM, "Telegram Bot API endpoint reachable", LogLevel.SUCCESS)
                }
            }
        } catch (e: Exception) {
            val wasReachable = _isTelegramReachable.value
            _isTelegramReachable.value = false
            if (wasReachable) {
                EventLogger.log(LogCategory.TELEGRAM, "Telegram Bot API unreachable: ${e.message}", LogLevel.WARNING)
            }
        }
    }
}
