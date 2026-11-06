package dev.anilbeesetti.nextplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDavConnectionTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun saveAndBrowseSubdirectoryWhenHeadIsForbidden() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.add(request)
                    if (request.method == "HEAD") return MockResponse().setResponseCode(403)
                    if (request.getHeader("Authorization") !=
                        Credentials.basic(
                            "viewer",
                            "secret",
                        )
                    ) {
                        return MockResponse().setResponseCode(401)
                            .addHeader(
                                "WWW-Authenticate",
                                "Basic realm=\"Test WebDAV\"",
                            )
                    }
                    if (request.method != "PROPFIND" || request.path != "/Steam/") {
                        return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setResponseCode(207)
                        .addHeader(
                            "Content-Type",
                            "application/xml; charset=utf-8",
                        )
                        .setBody(
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <d:multistatus xmlns:d="DAV:">
                              <d:response>
                                <d:href>/Steam/</d:href>
                                <d:propstat><d:prop>
                                  <d:resourcetype><d:collection/></d:resourcetype>
                                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                              </d:response>
                              <d:response>
                                <d:href>/Steam/webdav-regression.mp4</d:href>
                                <d:propstat><d:prop>
                                  <d:resourcetype/><d:getcontentlength>123</d:getcontentlength>
                                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                              </d:response>
                            </d:multistatus>
                            """.trimIndent(),
                        )
                }
            }
            server.start()
            val connectionName = "WebDAV regression ${server.port}"

            composeRule.onNodeWithContentDescription(label = text(R.string.network)).performClick()
            composeRule.onNodeWithTag(testTag = "top_level_fab").performClick()
            composeRule.onNodeWithText(text = "WEBDAV").performClick()
            replaceField(
                label = text(R.string.connection_name),
                value = connectionName,
            )
            replaceField(
                label = text(R.string.host),
                value = "127.0.0.1",
            )
            replaceField(
                label = composeRule.activity.getString(
                    R.string.port_hint,
                    80,
                ),
                value = server.port.toString(),
            )
            replaceField(
                label = text(R.string.path_share),
                value = "/Steam",
            )
            replaceField(
                label = text(R.string.username),
                value = "viewer",
            )
            replaceField(
                label = text(R.string.password),
                value = "secret",
            )
            composeRule.onNodeWithText(
                text = text(R.string.test_and_save),
            ).performScrollTo().performClick()

            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText(
                    text = connectionName,
                ).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText(
                        text = text(R.string.test_and_save),
                    ).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithText(text = connectionName).performClick()
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText(
                    text = "webdav-regression.mp4",
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(text = "webdav-regression.mp4").assertIsDisplayed()
            assertTrue(requests.none { it.method == "HEAD" })
            assertTrue(
                requests.count {
                    it.method == "PROPFIND" && it.getHeader("Depth") == "1"
                } >= 2,
            )
            assertTrue(
                requests.any {
                    it.getHeader("Authorization") ==
                        Credentials.basic(
                            "viewer",
                            "secret",
                        )
                },
            )
        }
    }

    private fun replaceField(label: String, value: String) {
        composeRule.onNodeWithText(
            text = label,
        ).performScrollTo().performTextReplacement(text = value)
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
}
