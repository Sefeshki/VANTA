package com.example

import android.app.Application
import android.content.Context
import androidx.compose.material3.SwitchColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SimFeature
import com.example.ui.components.vantaSwitchColors
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OtpProtectionControlCard
import com.example.ui.screens.SimControlScreen
import com.example.ui.theme.LocalVantaColors
import com.example.ui.theme.VantaBackground
import com.example.ui.theme.VantaPhosphorGreen
import com.example.ui.theme.VantaSuccess
import com.example.ui.theme.VantaTextMuted
import com.example.ui.theme.VantaTheme
import com.example.ui.theme.getVantaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToggleAndActiveColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testPhosphorGreenAndAmoledSwitchTokenValues() {
        val expectedPhosphorGreen = Color(0xFF39FF14)
        assertEquals(expectedPhosphorGreen, VantaPhosphorGreen)
        assertEquals(expectedPhosphorGreen, VantaSuccess)
        assertEquals(expectedPhosphorGreen, getVantaColors().success)
        assertEquals(Color(0xFF000000), VantaBackground)

        var resolvedSwitchColors: SwitchColors? = null
        var resolvedThemeSuccess: Color? = null

        composeRule.setContent {
            VantaTheme {
                resolvedThemeSuccess = LocalVantaColors.current.success
                resolvedSwitchColors = vantaSwitchColors()
            }
        }
        composeRule.waitForIdle()

        assertEquals(expectedPhosphorGreen, resolvedThemeSuccess)
        val sw = resolvedSwitchColors
        assertNotNull(sw)
        sw!!

        // ON state: vivid phosphor green #39FF14 track + pure white thumb
        assertEquals(expectedPhosphorGreen, sw.checkedTrackColor)
        assertEquals(expectedPhosphorGreen, sw.checkedBorderColor)
        assertEquals(Color.White, sw.checkedThumbColor)

        // OFF state: pure black AMOLED track (not gray) + muted thumb
        assertEquals(Color.Black, sw.uncheckedTrackColor)
        assertNotEquals(Color(0xFF141418), sw.uncheckedTrackColor)
        assertEquals(VantaTextMuted, sw.uncheckedThumbColor)
        assertEquals(Color.Black, sw.disabledUncheckedTrackColor)
        assertEquals(Color.Black, sw.disabledCheckedTrackColor)
    }

    @Test
    fun testWidgetActiveDrawableAndUpdaterUsePhosphorGreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawableResId = context.resources.getIdentifier("widget_dot_active", "drawable", context.packageName)
        assertTrue(drawableResId != 0)
        val drawable = context.getDrawable(drawableResId) as? android.graphics.drawable.GradientDrawable
        assertNotNull(drawable)
        assertEquals(android.graphics.Color.parseColor("#39FF14"), drawable!!.color?.defaultColor)

        val field = Class.forName("com.example.widget.VantaWidgetUpdater").getDeclaredField("ACTIVE_GREEN")
        field.isAccessible = true
        val activeGreenArgb = field.getInt(null)
        assertEquals(0xFF39FF14.toInt(), activeGreenArgb)
        assertEquals(VantaPhosphorGreen.toArgb(), activeGreenArgb)
    }

    @Test
    fun testDashboardStayAliveAndAutoStartTogglesPersistAndReflectState() {
        val app = ApplicationProvider.getApplicationContext<Application>() as VantaApplication
        app.preferencesManager.setTutorialCompleted(true)
        app.preferencesManager.setSeenPermissionSetup(true)
        app.preferencesManager.setStayAliveEnabled(true)
        app.preferencesManager.setAutoStartEnabled(false)

        composeRule.setContent {
            VantaTheme {
                DashboardScreen(
                    onNavigateToSimControl = {},
                    onNavigateToTelegram = {},
                    onNavigateToPermissions = {},
                    onNavigateToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stay_alive_switch").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("auto_start_switch").performScrollTo().assertIsOff()

        composeRule.onNodeWithTag("stay_alive_switch").performClick()
        composeRule.onNodeWithTag("auto_start_switch").performClick()
        composeRule.waitForIdle()

        assertFalse(app.preferencesManager.preferences.value.isStayAliveEnabled)
        assertTrue(app.preferencesManager.preferences.value.isAutoStartEnabled)
        composeRule.onNodeWithTag("stay_alive_switch").assertIsOff()
        composeRule.onNodeWithTag("auto_start_switch").assertIsOn()
    }

    @Test
    fun testSimControlSwitchesToggleAcrossSlotsAndFeatures() {
        val app = ApplicationProvider.getApplicationContext<Application>() as VantaApplication
        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_PHONE_STATE
        )
        val subMgr = app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
        val sub0 = org.robolectric.shadows.ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
            .setId(5)
            .setSimSlotIndex(0)
            .setCarrierName("Vodafone")
            .buildSubscriptionInfo()
        org.robolectric.Shadows.shadowOf(subMgr).setActiveSubscriptionInfos(sub0)
        app.simManager.refreshSims()

        app.preferencesManager.setSimMasterEnabled(0, true)
        app.preferencesManager.setSimFeatureEnabled(0, SimFeature.SMS_FORWARDING, true)
        app.preferencesManager.setSimFeatureEnabled(0, SimFeature.OTP_MESSAGES, true)

        composeRule.setContent {
            VantaTheme {
                SimControlScreen(onNavigateBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sim_master_switch_0").assertIsOn()
        composeRule.onNodeWithTag("sim_feature_0_OTP_MESSAGES").performScrollTo().assertIsOn()

        // Toggle OTP Messages OFF & back ON
        composeRule.onNodeWithTag("sim_feature_0_OTP_MESSAGES").performClick()
        composeRule.waitForIdle()
        assertFalse(app.preferencesManager.preferences.value.simControl.sim1.otpMessagesEnabled)
        composeRule.onNodeWithTag("sim_feature_0_OTP_MESSAGES").assertIsOff()

        composeRule.onNodeWithTag("sim_feature_0_OTP_MESSAGES").performClick()
        composeRule.waitForIdle()
        assertTrue(app.preferencesManager.preferences.value.simControl.sim1.otpMessagesEnabled)
        composeRule.onNodeWithTag("sim_feature_0_OTP_MESSAGES").assertIsOn()

        // Toggle SIM 1 master OFF & back ON
        composeRule.onNodeWithTag("sim_master_switch_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(app.preferencesManager.preferences.value.simControl.sim1.masterEnabled)
        composeRule.onNodeWithTag("sim_master_switch_0").assertIsOff()

        composeRule.onNodeWithTag("sim_master_switch_0").performClick()
        composeRule.waitForIdle()
        assertTrue(app.preferencesManager.preferences.value.simControl.sim1.masterEnabled)
        composeRule.onNodeWithTag("sim_master_switch_0").assertIsOn()
    }

    @Test
    fun testOtpProtectionCardToggleChangesState() {
        var enabled = false
        composeRule.setContent {
            VantaTheme {
                OtpProtectionControlCard(
                    isProtectionEnabled = enabled,
                    isOtpForwardingAvailable = true,
                    onToggle = { enabled = it },
                    switchTestTag = "privacy_otp_switch"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("privacy_otp_switch").assertIsOff().performClick()
        composeRule.waitForIdle()
        assertTrue(enabled)
    }
}
