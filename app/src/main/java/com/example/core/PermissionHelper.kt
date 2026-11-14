package com.example.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

object PermissionHelper {

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun areAllGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isGranted(context, it) }
    }

    fun missingPermissions(context: Context, permissions: List<String>): List<String> {
        return permissions.filterNot { isGranted(context, it) }
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
