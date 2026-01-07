package dev.anilbeesetti.nextplayer.crash

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.BuildConfig
import dev.anilbeesetti.nextplayer.MainActivity
import dev.anilbeesetti.nextplayer.MainActivityUiState
import dev.anilbeesetti.nextplayer.MainViewModel
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.shouldUseDarkTheme
import dev.anilbeesetti.nextplayer.shouldUseDynamicTheming
import dev.anilbeesetti.nextplayer.shouldUseHighContrastDarkTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        val exceptionString = intent.getStringExtra("exception") ?: ""
        var logcat by mutableStateOf("")

        lifecycleScope.launch {
            logcat = collectLogcat()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                val clipboard = LocalClipboard.current
                CrashScreen(
                    exceptionString = exceptionString,
                    logcat = logcat,
                    onShareLogsClick = {
                        lifecycleScope.launch {
                            shareLogs(
                                deviceInfo = collectDeviceInfo(),
                                exceptionString = exceptionString,
                                logcat = logcat,
                            )
                        }
                    },
                    onCopyLogsClick = {
                        clipboard.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                null,
                                concatLogs(collectDeviceInfo(), exceptionString, logcat),
                            ),
                        )
                    },
                    onRestartClick = {
                        finish()
                        startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                    },
                )
            }
        }
    }

    private suspend fun shareLogs(
        deviceInfo: String,
        exceptionString: String,
        logcat: String,
    ) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "next_player_logs.txt")
        if (file.exists()) file.delete()
        file.createNewFile()
        file.appendText(concatLogs(deviceInfo, exceptionString, logcat))
        val uri = FileProvider.getUriForFile(this@CrashActivity, BuildConfig.APPLICATION_ID + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.clipData = ClipData.newRawUri(null, uri)
        intent.type = "text/plain"
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(
            Intent.createChooser(intent, getString(R.string.crash_screen_share)),
        )
    }

    private fun concatLogs(
        deviceInfo: String,
        crashLogs: String? = null,
        logcat: String,
    ): String {
        return StringBuilder().apply {
            appendLine(deviceInfo)
            appendLine()
            if (!crashLogs.isNullOrBlank()) {
                appendLine("-".repeat(50))
                appendLine("Exception:")
                appendLine(crashLogs)
                appendLine()
            }
            appendLine("-".repeat(50))
            appendLine("Logcat:")
            appendLine(logcat)
        }.toString()
    }

    private suspend fun collectLogcat(): String = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime()
        val reader = BufferedReader(InputStreamReader(process.exec("logcat -d").inputStream))
        val logcat = StringBuilder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            reader.lines().forEach(logcat::appendLine)
        } else {
            reader.readLines().forEach(logcat::appendLine)
        }
        logcat.toString()
    }

    private fun collectDeviceInfo(): String = """
        App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Android version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})
        Device brand: ${Build.BRAND}
        Device manufacturer: ${Build.MANUFACTURER}
        Device model: ${Build.MODEL} (${Build.DEVICE})
    """.trimIndent()
}

@Composable
private fun CrashScreen(
    modifier: Modifier = Modifier,
    exceptionString: String,
    logcat: String,
    onShareLogsClick: () -> Unit = {},
    onCopyLogsClick: () -> Unit = {},
    onRestartClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            val borderColor = MaterialTheme.colorScheme.outline
            Column(
                Modifier
                    .drawBehind {
                        drawLine(
                            color = borderColor,
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = Dp.Hairline.value,
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onShareLogsClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.crash_screen_share))
                    }
                    FilledIconButton(onClick = onCopyLogsClick) {
                        Icon(imageVector = NextIcons.Copy, contentDescription = null)
                    }
                }
                OutlinedButton(
                    onClick = onRestartClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.crash_screen_restart))
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = NextIcons.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.crash_screen_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.crash_screen_subtitle, stringResource(R.string.app_name)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.crash_screen_logs_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            LogsSelectionContainer(logs = exceptionString)
            Text(
                text = stringResource(R.string.crash_screen_logcat),
                style = MaterialTheme.typography.headlineSmall,
            )
            LogsSelectionContainer(logs = logcat)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LogsSelectionContainer(
    logs: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text = logs,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun CrashLogsScreenPreview() {
    NextPlayerTheme {
        CrashScreen(
            exceptionString = "Exception message",
            logcat = "Logcat message",
        )
    }
}
