package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private var lastProbeTime: Long = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !_isInternetAvailable.value
            _isInternetAvailable.value = true
            if (wasOffline) {
                EventLogger.log(LogCategory.NETWORK, "Internet connection detected", LogLevel.SUCCESS)
            }
            checkTelegramReachability()
        }

        override fun onLost(network: Network) {
            val stillConnected = checkCurrentInternet()
            _isInternetAvailable.value = stillConnected
            if (!stillConnected) {
                _isTelegramReachable.value = false
                EventLogger.log(LogCategory.NETWORK, "Internet connection lost", LogLevel.WARNING)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val wasInternet = _isInternetAvailable.value
            _isInternetAvailable.value = hasInternet
            if (hasInternet && (!wasInternet || !_isTelegramReachable.value)) {
                checkTelegramReachability()
            } else if (!hasInternet) {
                _isTelegramReachable.value = false
            }
        }
    }

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        refresh()

        // Periodic Telegram probe (every 30 seconds when internet is available)
        scope.launch {
            while (isActive) {
                delay(30000)
                if (_isInternetAvailable.value) {
                    testTelegramConnection()
                }
            }
        }
    }

    fun refresh() {
        val currentInternet = checkCurrentInternet()
        _isInternetAvailable.value = currentInternet
        if (currentInternet) {
            checkTelegramReachability()
        } else {
            _isTelegramReachable.value = false
        }
    }

    fun checkCurrentInternet(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun checkTelegramReachability() {
        val now = System.currentTimeMillis()
        if (now - lastProbeTime < 2000L && _isTelegramReachable.value) {
            return // Debounce rapid probes
        }

        reachabilityJob?.cancel()
        reachabilityJob = scope.launch {
            delay(150) // Small debounce
            testTelegramConnection()
        }
    }

    private fun testTelegramConnection() {
        lastProbeTime = System.currentTimeMillis()
        try {
            // Direct socket probe to api.telegram.org:443 with 3.5s timeout
            Socket().use { socket ->
                socket.connect(InetSocketAddress("api.telegram.org", 443), 3500)
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

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        reachabilityJob?.cancel()
        scope.cancel()
    }
}
