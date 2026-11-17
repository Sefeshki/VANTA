package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.core.ManualSetupTarget
import com.example.core.PermissionHelper
import com.example.core.PreferencesManager
import com.example.ui.components.FirstLaunchPermissionDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.theme.VantaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionSetupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: VantaApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.preferencesManager.setOtpProtectionEnabled(false)
        app.preferencesManager.setSeenOtpSuggestion(false)
        app.preferencesManager.setSeenPermissionSetup(false)
        app.preferencesManager.setTutorialCompleted(true)
    }

    @Test
    fun testFirstLaunchPopupReplacesOtpDialogAndNavigatesWithoutChangingOtpState() {
        var navigatedToPermissions = false

        composeRule.setContent {
            VantaTheme {
                DashboardScreen(
                    onNavigateToSimControl = {},
                    onNavigateToTelegram = {},
                    onNavigateToPermissions = { navigatedToPermissions = true },
                    onNavigateToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        // New first-launch popup is displayed with exact copy; old OTP popup is not shown
        composeRule.onNodeWithTag("first_launch_permission_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to VANTA").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Give VANTA all the permissions it needs for the best experience. This helps ensure that SMS forwarding, SIM management, notifications and background services work correctly."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("otp_suggestion_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("Keep your codes private?").assertDoesNotExist()

        // OTP protection must remain disabled (default) and untouched
        assertFalse(app.preferencesManager.preferences.value.isOtpProtectionEnabled)

        // Clicking Continue navigates to Permissions and marks popup seen without toggling OTP
        composeRule.onNodeWithTag("first_launch_permission_continue_btn").performClick()
        composeRule.waitForIdle()

        assertTrue("Clicking Continue must navigate to Permissions page", navigatedToPermissions)
        assertTrue(app.preferencesManager.preferences.value.hasSeenPermissionSetup)
        assertFalse(
            "First-launch permission setup must NOT enable or disable OTP",
            app.preferencesManager.preferences.value.isOtpProtectionEnabled
        )
    }

    @Test
    fun testFirstLaunchPermissionDialogDismissMarksSeenWithoutNavigatingOrChangingOtp() {
        var isVisible by mutableStateOf(true)
        var continueClicked = false

        composeRule.setContent {
            VantaTheme {
                FirstLaunchPermissionDialog(
                    isVisible = isVisible,
                    onContinue = { continueClicked = true },
                    onDismiss = { isVisible = false }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("first_launch_permission_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("dismiss_first_launch_permission_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("first_launch_permission_dialog").assertDoesNotExist()
        assertFalse(continueClicked)
        assertFalse(app.preferencesManager.preferences.value.isOtpProtectionEnabled)
    }

    @Test
    fun testPermissionHelperGroupsRuntimeRequestsExcludesPermanentlyDeniedAndDetectsBattery() {
        val prefsMgr = PreferencesManager(app)
        val shadowApp = shadowOf(app)

        val powerMgr = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerMgr).setIgnoringBatteryOptimizations(app.packageName, false)

        // Initially all runtime permissions + battery exemption are missing
        val initial = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertFalse(initial.isSetupComplete)
        assertTrue(initial.isBatteryOptimizationMissing)
        assertEquals(PermissionHelper.runtimePermissionsForCurrentApi(), initial.requestableRuntimePermissions)
        assertTrue(initial.permanentlyDeniedPermissions.isEmpty())
        assertEquals(ManualSetupTarget.NONE, initial.manualTarget)

        // Permanently deny RECEIVE_SMS while others remain requestable
        prefsMgr.markPermissionsRequested(listOf(Manifest.permission.RECEIVE_SMS))
        val partialDenied = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertFalse(partialDenied.requestableRuntimePermissions.contains(Manifest.permission.RECEIVE_SMS))
        assertEquals(listOf(Manifest.permission.RECEIVE_SMS), partialDenied.permanentlyDeniedPermissions)
        assertTrue(partialDenied.manualGuidanceMessage?.contains("SMS Reception") == true)

        // Grant all runtime permissions except RECEIVE_SMS -> manualTarget switches to APP_SETTINGS
        val allOthers = PermissionHelper.runtimePermissionsForCurrentApi()
            .filterNot { it == Manifest.permission.RECEIVE_SMS }
            .toTypedArray()
        shadowApp.grantPermissions(*allOthers)
        val needAppSettings = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertEquals(ManualSetupTarget.APP_SETTINGS, needAppSettings.manualTarget)
        assertTrue(needAppSettings.requestableRuntimePermissions.isEmpty())
        assertFalse(needAppSettings.isSetupComplete)

        // Grant RECEIVE_SMS -> tracking clears on sync, and only Battery Exemption remains
        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS)
        PermissionHelper.syncGrantedPermissionsTracking(app, prefsMgr)
        assertFalse(prefsMgr.wasPermissionRequested(Manifest.permission.RECEIVE_SMS))

        val needBattery = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertEquals(ManualSetupTarget.BATTERY_OPTIMIZATION_SETTINGS, needBattery.manualTarget)
        assertFalse(needBattery.isSetupComplete)
        assertEquals(listOf(PermissionHelper.LABEL_BATTERY_OPTIMIZATION), needBattery.missingItemLabels)

        // Exempt battery optimization -> setup becomes complete
        shadowOf(powerMgr).setIgnoringBatteryOptimizations(app.packageName, true)
        val complete = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertTrue(complete.isSetupComplete)
        assertTrue(complete.missingItemLabels.isEmpty())
        assertEquals(ManualSetupTarget.NONE, complete.manualTarget)

        // Revoke RECEIVE_SMS later -> no longer permanently denied, setup incomplete again
        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS)
        val revoked = PermissionHelper.inspectPermissionSetupState(app, prefsMgr) { false }
        assertFalse(revoked.isSetupComplete)
        assertTrue(revoked.requestableRuntimePermissions.contains(Manifest.permission.RECEIVE_SMS))
    }

    @Test
    fun testPermissionCenterSetupCardVisibilityRequestsSettingsAndRefreshOnResume() {
        val shadowApp = shadowOf(app)
        val powerMgr = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerMgr).setIgnoringBatteryOptimizations(app.packageName, false)

        val launchedPermissionRequests = mutableListOf<Array<String>>()
        val fakeRegistryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
                @Suppress("UNCHECKED_CAST")
                override fun <I : Any?, O : Any?> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    if (contract is ActivityResultContracts.RequestMultiplePermissions && input is Array<*>) {
                        val perms = input.filterIsInstance<String>().toTypedArray()
                        launchedPermissionRequests.add(perms)
                        // Grant SMS, Phone, Notification, Call Log, Contacts except leave Battery missing
                        shadowApp.grantPermissions(*perms)
                        val resultMap = perms.associateWith { true }
                        dispatchResult(requestCode, resultMap as O)
                    }
                }
            }
        }

        composeRule.setContent {
            CompositionLocalProvider(
                androidx.activity.compose.LocalActivityResultRegistryOwner provides fakeRegistryOwner
            ) {
                VantaTheme {
                    PermissionCenterScreen(onNavigateBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        // Complete Your Setup card is visible above individual permissions
        composeRule.onNodeWithTag("complete_setup_card").assertIsDisplayed()
        composeRule.onNodeWithText("Complete Your Setup").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Grant all the permissions VANTA needs to provide the best experience and keep its features working correctly."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("grant_all_permissions_btn").assertIsDisplayed()

        // First click: requests grouped missing runtime permissions
        composeRule.onNodeWithTag("grant_all_permissions_btn").performClick()
        composeRule.waitForIdle()

        assertEquals(1, launchedPermissionRequests.size)
        val requested = launchedPermissionRequests.first().toList()
        assertTrue(requested.containsAll(listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        }

        // Card MUST stay visible because Battery Saver Exemption still requires manual action
        composeRule.onNodeWithTag("complete_setup_card").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_manual_guidance_text").assertIsDisplayed()

        // Second click: guides to Android Battery Optimization Settings
        composeRule.onNodeWithTag("grant_all_permissions_btn").performClick()
        composeRule.waitForIdle()

        val startedIntent: Intent? = shadowOf(composeRule.activity).nextStartedActivity
        assertNotNull("Must open Battery Optimization Settings when manual setup is required", startedIntent)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, startedIntent?.action)

        // Card still MUST NOT hide simply because user clicked the button
        composeRule.onNodeWithTag("complete_setup_card").assertIsDisplayed()

        // Simulate user enabling Battery Exemption in Android Settings and returning (ON_RESUME)
        shadowOf(powerMgr).setIgnoringBatteryOptimizations(app.packageName, true)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        // Card automatically hides once all required permissions are granted/configured
        composeRule.onNodeWithTag("complete_setup_card").assertDoesNotExist()

        // Individual permission cards remain visible and functional
        composeRule.onNodeWithText("SMS Reception").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Battery Saver Exemption").performScrollTo().assertIsDisplayed()

        // Revoking a permission and resuming brings Complete Your Setup card back automatically
        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("complete_setup_card").performScrollTo().assertIsDisplayed()
    }
}
