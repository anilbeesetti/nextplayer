package com.graviton.settings.screens.about

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.components.ClickablePreferenceItem
import com.graviton.core.ui.components.ListSectionTitle
import com.graviton.core.ui.components.NextTopAppBar
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.core.ui.extensions.openInBrowserOrToast
import com.graviton.core.ui.theme.gravitonScreenContainerColor
import com.graviton.settings.utils.rememberTvListFocusRequester
import com.graviton.settings.utils.tvFocusDown
import com.graviton.settings.utils.tvListFocus
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/librespark-crypto/graviton"
private const val KOFI_URL = "https://ko-fi.com/graviton"
private const val PAYPAL_URL = "https://paypal.me/graviton"
private const val UPI_ID = "graviton@oksbi"

/**
 * The About screen: app identity, open-source libraries, repository and ways to support
 * development. All external links go through [openInBrowserOrToast], which launches a standard
 * Android view intent and fails with a toast when no handler exists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutPreferencesScreen(
    onLibrariesClick: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val listFocusRequester = rememberTvListFocusRequester()
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.about_name),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp, modifier = Modifier.tvFocusDown(listFocusRequester)) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = gravitonScreenContainerColor(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .tvListFocus(listFocusRequester)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(vertical = 16.dp),
        ) {
            AboutApp(
                onGithubClick = { context.openInBrowserOrToast(GITHUB_URL) },
                onLibrariesClick = onLibrariesClick,
            )
            ListSectionTitle(text = stringResource(id = R.string.donate))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(R.string.kofi),
                    description = stringResource(R.string.support_the_developer_on, stringResource(R.string.kofi)),
                    icon = ImageVector.vectorResource(R.drawable.ic_kofi),
                    onClick = { context.openInBrowserOrToast(KOFI_URL) },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.paypal),
                    description = stringResource(R.string.support_the_developer_on, stringResource(R.string.paypal)),
                    icon = ImageVector.vectorResource(R.drawable.ic_paypal),
                    onClick = { context.openInBrowserOrToast(PAYPAL_URL) },
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.upi),
                    description = UPI_ID,
                    icon = ImageVector.vectorResource(R.drawable.ic_upi),
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", UPI_ID)))
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        }
                    },
                    isLastItem = true,
                )
            }
        }
    }
}

@Composable
fun AboutApp(
    modifier: Modifier = Modifier,
    onGithubClick: () -> Unit,
    onLibrariesClick: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember { context.appVersion() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_graviton_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.large),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.version_and_developer,
                            appVersion,
                            stringResource(R.string.app_developer),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onLibrariesClick,
                    shape = MaterialTheme.shapes.large,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = NextIcons.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.libraries))
                }
                Button(
                    onClick = onGithubClick,
                    shape = MaterialTheme.shapes.large,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.github))
                }
            }
        }
    }
}

/** The version name shown in About, e.g. "1.0.0". */
private fun Context.appVersion(): String {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        packageInfo.versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
