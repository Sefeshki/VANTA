package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.UpdateState
import com.example.data.model.AppPreferences
import com.example.data.model.TelegramConfig
import com.example.ui.components.blurBitmapInPlace
import com.example.ui.screens.RightNavigationOverlay
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.unit.dp

/**
 * Tests specifically targeting Android 10 (API 29) fallback blur behavior
 * and drawer rendering over the blurred backdrop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class Android10DrawerBlurTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun android10UsesFallbackPathAndBlursDashboardWithoutBlackout() {
        assertEquals(Build.VERSION_CODES.Q, Build.VERSION.SDK_INT)

        // Simulate a 1/4-scaled 720x1280 Android 10 dashboard frame (180x320)
        // with AMOLED black background and bright white power ring + card strokes
        val width = 180
        val height = 320
        val captured = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        captured.eraseColor(Color.BLACK)

        // Draw white power button region around (90, 80) and card border around y = 180
        for (y in 60..100) {
            for (x in 70..110) {
                if (x == 70 || x == 110 || y == 60 || y == 100 || (x in 86..94 && y in 76..84)) {
                    captured.setPixel(x, y, Color.WHITE)
                }
            }
        }
        for (x in 15..165) {
            captured.setPixel(x, 180, Color.WHITE)
            captured.setPixel(x, 181, Color.WHITE)
        }

        val blurred = blurBitmapInPlace(captured, radius = 12)
        assertNotNull("Android 10 fallback blur must produce an ImageBitmap for visible dashboard content", blurred)

        val out = blurred!!.asAndroidBitmap()
        // Surrounding pixels near the white core must receive diffused non-black glow
        val nearbyColor = out.getPixel(90, 90)
        val haloColor = out.getPixel(90, 108)
        assertEquals(0xFF, Color.alpha(nearbyColor))
        assertTrue("Blurred core region must remain visibly illuminated, not black", Color.red(nearbyColor) > 15)
        assertTrue("Blur must soften and diffuse into adjacent black background", Color.red(haloColor) > 5)
    }

    @Test
    fun android10RejectsCompletelyBlackCaptureSoLiveDashboardIsNeverHidden() {
        val blankCapture = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
        blankCapture.eraseColor(Color.BLACK)

        val blurred = blurBitmapInPlace(blankCapture, radius = 12)
        assertNull("Empty/black capture must return null instead of painting solid black", blurred)
    }

    @Test
    fun android10DrawerRendersBlurredBackdropAndStaticLogoCorrectly() {
        val sample = Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888)
        sample.eraseColor(Color.BLACK)
        for (y in 70..95) {
            for (x in 75..105) {
                sample.setPixel(x, y, Color.WHITE)
            }
        }
        val blurredBackdrop = blurBitmapInPlace(sample, radius = 12)!!

        composeRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = blurredBackdrop,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("fallback_blurred_backdrop")
                    )

                    RightNavigationOverlay(
                        isOpen = true,
                        topOffsetDp = 10.dp,
                        closeButtonReservedWidth = 56.dp,
                        prefs = AppPreferences(),
                        telegramConfig = TelegramConfig(),
                        isServiceRunning = false,
                        updateState = UpdateState.Idle,
                        onDismiss = {},
                        onOpenUpdateModal = {},
                        onNavigateToTelegram = {},
                        onNavigateToPrivacyProtection = {},
                        onNavigateToSystemHealth = {},
                        onReplayTutorial = {},
                        onOpenGithub = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("fallback_blurred_backdrop").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_drawer_content").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_branding_logo", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Your phone. Your SIM. Your Telegram.", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
