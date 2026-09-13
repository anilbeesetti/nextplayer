package dev.anilbeesetti.nextplayer

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.provider.MediaStore
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MainActivityNavigationAnimationTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchingTabsKeepsTheSameBottomNavigationBar() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val home = composeRule.activity.getString(R.string.home)
        val playlists = composeRule.activity.getString(R.string.playlists)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(home).fetchSemanticsNodes().isNotEmpty()
        }
        val homeNode = composeRule.onNodeWithContentDescription(home).fetchSemanticsNode()
        try {
            composeRule.mainClock.autoAdvance = false
            composeRule.onNodeWithContentDescription(playlists).performClick()
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onAllNodesWithContentDescription(home).assertCountEquals(1)
            val animatedHomeNode = composeRule.onNodeWithContentDescription(home).fetchSemanticsNode()
            assertEquals(homeNode.id, animatedHomeNode.id)
            assertEquals(homeNode.boundsInRoot, animatedHomeNode.boundsInRoot)
        } finally {
            composeRule.mainClock.autoAdvance = true
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun quickFolderBackRestoresLandscapeLayout() {
        val resolver = composeRule.activity.contentResolver
        val folder = "NavigationAnimationTest"
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "navigation.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$folder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                },
            ),
        )
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("navigation.mp4").use { input ->
                checkNotNull(resolver.openOutputStream(uri)).use(input::copyTo)
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText(folder).fetchSemanticsNodes().isNotEmpty()
            }
            val appName = composeRule.activity.getString(R.string.app_name)
            val navigateUp = composeRule.activity.getString(R.string.navigate_up)
            val homeBounds = composeRule.onNodeWithText(appName).getUnclippedBoundsInRoot()
            for (delay in listOf(64L, 256L)) {
                composeRule.mainClock.autoAdvance = false
                composeRule.onNodeWithText(folder).performClick()
                composeRule.mainClock.advanceTimeBy(delay)
                if (delay == 64L) {
                    composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
                } else {
                    composeRule.onNodeWithContentDescription(navigateUp).performClick()
                }
                composeRule.mainClock.advanceTimeBy(1_000)
                composeRule.mainClock.autoAdvance = true
                composeRule.onNodeWithText(appName).assertIsDisplayed()
                assertEquals(homeBounds, composeRule.onNodeWithText(appName).getUnclippedBoundsInRoot())
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
            resolver.delete(uri, null, null)
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
