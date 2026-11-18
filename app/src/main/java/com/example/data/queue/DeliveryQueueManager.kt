package com.example.data.queue

import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.DeliveryQueueItem
import com.example.data.model.DeliveryStatus
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SmsMessageItem
import com.example.telegram.TelegramBotManager
import com.example.telephony.ContactResolver
import com.example.telephony.SimManager
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
    private val preferencesManager: PreferencesManager,
    private val simManager: SimManager? = null,
    private val contactResolver: ContactResolver? = null,
    private val onDeliverySuccess: (() -> Unit)? = null,
    private val onDeliveryDropped: ((String) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mutex = Mutex()
    private val fingerprintLock = Any()
    private val queue = mutableListOf<DeliveryQueueItem>()

    // Fingerprints of active queued items and items delivered in the last 15 minutes
    private val activeFingerprints = mutableSetOf<String>()
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
        val cutoff = now - (15 * 60 * 1000)

        synchronized(fingerprintLock) {
            processedFingerprints.entries.removeIf { it.value < cutoff }
            if (processedFingerprints.containsKey(fingerprint) || !activeFingerprints.add(fingerprint)) {
                EventLogger.log(
                    LogCategory.SMS_DELIVERY,
                    "Duplicate SMS detected from '${sms.sender}' — skipped to prevent duplicate forwarding",
                    LogLevel.WARNING
                )
                return false
            }
        }

        val item = DeliveryQueueItem(
            id = UUID.randomUUID().toString(),
            sms = sms,
            timestamp = now
        )

        scope.launch {
            val size = mutex.withLock {
                queue.add(item)
                val pending = queue.count { it.status == DeliveryStatus.PENDING }
                _pendingCount.value = pending
                pending
            }
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Enqueued SMS from '${sms.sender}' for delivery (Queue size: $size)",
                LogLevel.INFO
            )
        }
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
                delay(800)
            }
        }
    }

    private suspend fun processItem(item: DeliveryQueueItem) {
        val simLabel = simManager?.formatSimDisplayLabel(item.sms.simSlot, item.sms.subId)
            ?: if (item.sms.simSlot >= 0) "SIM ${item.sms.simSlot + 1}" else "Unknown SIM"
        val contactName = contactResolver?.resolveContactName(item.sms.sender)

        item.attemptCount++
        item.lastAttemptTime = System.currentTimeMillis()

        val result = telegramBotManager.forwardSms(
            sender = item.sms.sender,
            body = item.sms.body,
            simLabel = simLabel,
            contactName = contactName,
            receivedTimestamp = item.sms.timestamp,
            isOtp = item.sms.isOtp,
            simSlot = item.sms.simSlot,
            subId = item.sms.subId
        )

        val fingerprint = computeFingerprint(item.sms)
        var backoffDelay = 0L
        var notifySuccess = false
        var droppedError: String? = null

        mutex.withLock {
            if (result.isSuccess) {
                item.status = DeliveryStatus.DELIVERED
                synchronized(fingerprintLock) {
                    activeFingerprints.remove(fingerprint)
                    processedFingerprints[fingerprint] = System.currentTimeMillis()
                }
                queue.remove(item)
                notifySuccess = true
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                item.lastError = errorMsg
                if (item.attemptCount >= MAX_RETRIES) {
                    item.status = DeliveryStatus.FAILED
                    synchronized(fingerprintLock) {
                        activeFingerprints.remove(fingerprint)
                    }
                    queue.remove(item)
                    EventLogger.log(
                        LogCategory.ERROR,
                        "SMS from '${item.sms.sender}' dropped after $MAX_RETRIES delivery attempts: $errorMsg",
                        LogLevel.ERROR
                    )
                    droppedError = errorMsg
                } else {
                    backoffDelay = (1000L * (1 shl item.attemptCount)).coerceAtMost(30000L)
                    EventLogger.log(
                        LogCategory.SMS_DELIVERY,
                        "Delivery attempt ${item.attemptCount} failed ($errorMsg), retrying in ${backoffDelay / 1000}s",
                        LogLevel.WARNING
                    )
                }
            }
            _pendingCount.value = queue.count { it.status == DeliveryStatus.PENDING }
        }

        if (notifySuccess) {
            onDeliverySuccess?.invoke()
        }
        if (droppedError != null) {
            onDeliveryDropped?.invoke(droppedError)
        }
        if (backoffDelay > 0L) {
            delay(backoffDelay)
        }
    }

    private fun computeFingerprint(sms: SmsMessageItem): String {
        val timeBucket = sms.timestamp / 30000
        val raw = "${sms.sender}:${sms.body.trim()}:$timeBucket:${sms.simSlot}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
