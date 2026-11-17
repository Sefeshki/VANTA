package com.example.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.data.model.SimFeature

data class PermissionRequirement(
    val feature: SimFeature?,
    val permissions: List<String>,
    val title: String,
    val rationale: String,
    val settingsHint: String
)

enum class ManualSetupTarget {
    NONE,
    APP_SETTINGS,
    BATTERY_OPTIMIZATION_SETTINGS
}

data class PermissionSetupSummary(
    val missingRuntimePermissions: List<String>,
    val requestableRuntimePermissions: List<String>,
    val permanentlyDeniedPermissions: List<String>,
    val isBatteryOptimizationMissing: Boolean,
    val missingItemLabels: List<String>,
    val isSetupComplete: Boolean,
    val manualTarget: ManualSetupTarget,
    val manualGuidanceMessage: String?
)

object PermissionHelper {

    const val LABEL_BATTERY_OPTIMIZATION = "Battery Saver Exemption"

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isGranted(context, it) }
    }

    fun missingPermissions(context: Context, permissions: List<String>): List<String> {
        return permissions.filterNot { isGranted(context, it) }
    }

    fun phonePermissionsForCurrentApi(): List<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }

    fun notificationPermissionsForCurrentApi(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Ordered sensibly by Android permission group so RequestMultiplePermissions
     * groups related permissions (SMS, Notifications, Phone, Call Log, Contacts).
     */
    fun runtimePermissionsForCurrentApi(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.SEND_SMS)
        addAll(notificationPermissionsForCurrentApi())
        addAll(phonePermissionsForCurrentApi())
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
    }

    fun permissionGroupLabel(permission: String): String = when (permission) {
        Manifest.permission.RECEIVE_SMS -> "SMS Reception"
        Manifest.permission.SEND_SMS -> "Telegram SMS Replies"
        Manifest.permission.POST_NOTIFICATIONS -> "Foreground Notification"
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS -> "SIM Card Info"
        Manifest.permission.READ_CALL_LOG -> "Call Log & Caller ID"
        Manifest.permission.READ_CONTACTS -> "Contact Name Lookup"
        else -> permission.substringAfterLast('.')
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun syncGrantedPermissionsTracking(context: Context, preferencesManager: PreferencesManager) {
        val granted = runtimePermissionsForCurrentApi().filter { isGranted(context, it) }
        preferencesManager.clearPermissionsRequested(granted)
    }

    fun inspectPermissionSetupState(
        context: Context,
        preferencesManager: PreferencesManager,
        shouldShowRationale: ((String) -> Boolean)? = null
    ): PermissionSetupSummary {
        val allRuntime = runtimePermissionsForCurrentApi()
        val missingRuntime = missingPermissions(context, allRuntime)
        val activity = if (shouldShowRationale == null) context.findActivity() else null

        val permanentlyDenied = missingRuntime.filter { perm ->
            val requested = preferencesManager.wasPermissionRequested(perm)
            if (!requested) {
                false
            } else {
                val showRationale = shouldShowRationale?.invoke(perm)
                    ?: activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, perm) }
                    ?: true
                !showRationale
            }
        }

        val permanentlyDeniedSet = permanentlyDenied.toSet()
        val requestable = missingRuntime.filterNot { it in permanentlyDeniedSet }
        val batteryMissing = !isBatteryOptimizationIgnored(context)

        val missingLabels = buildList {
            for (perm in missingRuntime) {
                val label = permissionGroupLabel(perm)
                if (!contains(label)) add(label)
            }
            if (batteryMissing) {
                add(LABEL_BATTERY_OPTIMIZATION)
            }
        }

        val deniedLabels = permanentlyDenied.map { permissionGroupLabel(it) }.distinct()

        val manualTarget = when {
            requestable.isNotEmpty() -> ManualSetupTarget.NONE
            permanentlyDenied.isNotEmpty() -> ManualSetupTarget.APP_SETTINGS
            batteryMissing -> ManualSetupTarget.BATTERY_OPTIMIZATION_SETTINGS
            else -> ManualSetupTarget.NONE
        }

        val guidance = when {
            permanentlyDenied.isNotEmpty() && requestable.isEmpty() ->
                "Manual action required for ${deniedLabels.joinToString(", ")}. Android does not allow another prompt—tap Grant All Permissions to enable them in App Settings."
            permanentlyDenied.isNotEmpty() ->
                "After requesting remaining permissions, enable ${deniedLabels.joinToString(", ")} manually in Android Settings."
            requestable.isEmpty() && batteryMissing ->
                "Runtime permissions are ready. Battery Saver Exemption requires manual selection in Android Settings."
            else -> null
        }

        return PermissionSetupSummary(
            missingRuntimePermissions = missingRuntime,
            requestableRuntimePermissions = requestable,
            permanentlyDeniedPermissions = permanentlyDenied,
            isBatteryOptimizationMissing = batteryMissing,
            missingItemLabels = missingLabels,
            isSetupComplete = missingRuntime.isEmpty() && !batteryMissing,
            manualTarget = manualTarget,
            manualGuidanceMessage = guidance
        )
    }

    fun requiredPermissionsForFeature(feature: SimFeature): List<String> = when (feature) {
        SimFeature.SMS_FORWARDING -> listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        SimFeature.OTP_MESSAGES -> listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        SimFeature.CALL_LOG_FORWARDING -> listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
        SimFeature.INCOMING_CALL_NOTIFICATIONS -> listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        SimFeature.SMS_SENDING -> listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    fun requirementForFeature(feature: SimFeature): PermissionRequirement = when (feature) {
        SimFeature.SMS_FORWARDING -> PermissionRequirement(
            feature = feature,
            permissions = requiredPermissionsForFeature(feature),
            title = "SMS & Phone Access Required",
            rationale = "VANTA needs SMS Reception and Phone State access to read arriving text messages on this SIM and route them to Telegram.",
            settingsHint = "Enable SMS and Phone permissions in Android Settings."
        )
        SimFeature.OTP_MESSAGES -> PermissionRequirement(
            feature = feature,
            permissions = requiredPermissionsForFeature(feature),
            title = "SMS Reception Required",
            rationale = "VANTA needs SMS Reception and Phone State access to inspect verification codes on this SIM.",
            settingsHint = "Enable SMS and Phone permissions in Android Settings."
        )
        SimFeature.CALL_LOG_FORWARDING -> PermissionRequirement(
            feature = feature,
            permissions = requiredPermissionsForFeature(feature),
            title = "Call Log Access Required",
            rationale = "VANTA needs Call Log and Phone State access to detect incoming, outgoing, and missed calls on this SIM along with duration and SIM slot.",
            settingsHint = "Enable Call Logs and Phone permissions in Android Settings."
        )
        SimFeature.INCOMING_CALL_NOTIFICATIONS -> PermissionRequirement(
            feature = feature,
            permissions = requiredPermissionsForFeature(feature),
            title = "Phone & Caller ID Access Required",
            rationale = "VANTA needs Phone State and Call Log access to detect ringing calls in real time and identify the caller number.",
            settingsHint = "Enable Phone and Call Logs permissions in Android Settings."
        )
        SimFeature.SMS_SENDING -> PermissionRequirement(
            feature = feature,
            permissions = requiredPermissionsForFeature(feature),
            title = "Send SMS Permission Required",
            rationale = "VANTA needs Send SMS and Phone State access so you can reply to forwarded messages directly from Telegram using this SIM.",
            settingsHint = "Enable SMS permission in Android Settings."
        )
    }

    fun isFeaturePermissionMissing(context: Context, feature: SimFeature): Boolean {
        return missingPermissions(context, requiredPermissionsForFeature(feature)).isNotEmpty()
    }

    fun isAnyPermanentlyDenied(
        context: Context,
        preferencesManager: PreferencesManager,
        permissions: List<String>
    ): Boolean {
        val activity = context.findActivity() ?: return false
        val missing = missingPermissions(context, permissions)
        if (missing.isEmpty()) return false
        return missing.any { perm ->
            preferencesManager.wasPermissionRequested(perm) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context.findActivity() == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                if (context.findActivity() == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
