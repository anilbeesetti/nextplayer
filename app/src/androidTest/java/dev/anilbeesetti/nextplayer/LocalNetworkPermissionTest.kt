package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class LocalNetworkPermissionTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        storagePermission,
        Manifest.permission.ACCESS_LOCAL_NETWORK,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun grantedLocalNetworkPermissionAllowsAddingConnectionAfterRecreation() {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(composeRule.activity, Manifest.permission.ACCESS_LOCAL_NETWORK),
        )
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.network)).performClick()
        composeRule.onNodeWithTag("top_level_fab").performClick()
        val host = composeRule.activity.getString(R.string.host)
        composeRule.onNodeWithText(host).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(host).assertIsDisplayed()
    }
}
