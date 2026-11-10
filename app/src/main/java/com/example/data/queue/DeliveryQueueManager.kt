package com.example.data.queue

import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.DeliveryQueueItem
import com.example.data.model.DeliveryStatus
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SmsMessageItem
import com.example.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

class DeliveryQueueManager(
    private val telegramBotManager: TelegramBotManager,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mutex = Mutex()
    private val queue = mutableListOf<DeliveryQueueItem>()

    // Set of message fingerprints delivered or processed in the last 15 minutes to prevent duplicates
    private val processedFingerprints = LinkedHashMap<String, Long>()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var workerJob: Job? = null

    init {
        startWorker()
    }

    fun enqueue(sms: SmsMessageItem): Boolean {
        val fingerprint = computeFingerprint(sms)
        val now = System.currentTimeMillis()

        // Clean up old fingerprints > 15 minutes
        val cutoff = now - (15 * 60 * 1000)
        processedFingerprints.entries.removeIf { it.value < cutoff }

        if (processedFingerprints.containsKey(fingerprint)) {
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Duplicate SMS detected from '${sms.sender}' — skipped to prevent duplicate forwarding",
                LogLevel.WARNING
            )
            return false
        }

        val item = DeliveryQueueItem(
            id = UUID.randomUUID().toString(),
            sms = sms,
            timestamp = now
        )

        scope.launch {
            mutex.withLock {
                queue.add(item)
                _pendingCount.value = queue.count { it.status == DeliveryStatus.PENDING }
            }
        }

        EventLogger.log(
            LogCategory.SMS_DELIVERY,
            "Enqueued SMS from '${sms.sender}' for delivery (Queue size: ${_pendingCount.value + 1})",
            LogLevel.INFO
        )
        return true
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = scope.launch {
            while (isActive) {
                val nextItem = mutex.withLock {
                    queue.firstOrNull { it.status == DeliveryStatus.PENDING }
                }

                if (nextItem == null) {
                    delay(1000)
                    continue
                }

                processItem(nextItem)
                delay(800) // Small safety delay between deliveries
            }
        }
    }

    private suspend fun processItem(item: DeliveryQueueItem) {
        val simLabel = if (item.sms.simSlot >= 0) "SIM${item.sms.simSlot + 1}" else null
        item.attemptCount++
        item.lastAttemptTime = System.currentTimeMillis()

        val result = telegramBotManager.forwardSms(
            sender = item.sms.sender,
            body = item.sms.body,
            simLabel = simLabel
        )

        mutex.withLock {
            if (result.isSuccess) {
                item.status = DeliveryStatus.DELIVERED
                processedFingerprints[computeFingerprint(item.sms)] = System.currentTimeMillis()
                queue.remove(item)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                item.lastError = errorMsg
                if (item.attemptCount >= MAX_RETRIES) {
                    item.status = DeliveryStatus.FAILED
                    queue.remove(item)
                    EventLogger.log(
                        LogCategory.ERROR,
                        "SMS from '${item.sms.sender}' dropped after $MAX_RETRIES delivery attempts: $errorMsg",
                        LogLevel.ERROR
                    )
                } else {
                    // Exponential backoff delay
                    val backoffDelay = (1000L * (1 shl item.attemptCount)).coerceAtMost(30000L)
                    EventLogger.log(
                        LogCategory.SMS_DELIVERY,
                        "Delivery attempt ${item.attemptCount} failed ($errorMsg), retrying in ${backoffDelay / 1000}s",
                        LogLevel.WARNING
                    )
                    delay(backoffDelay)
                }
            }
            _pendingCount.value = queue.count { it.status == DeliveryStatus.PENDING }
        }
    }

    private fun computeFingerprint(sms: SmsMessageItem): String {
        // Round timestamp to 30 second bucket
        val timeBucket = sms.timestamp / 30000
        val raw = "${sms.sender}:${sms.body.trim()}:$timeBucket:${sms.simSlot}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
