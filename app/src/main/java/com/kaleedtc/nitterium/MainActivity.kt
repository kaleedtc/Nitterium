package com.kaleedtc.nitterium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaleedtc.nitterium.ui.NitteriumApp
import com.kaleedtc.nitterium.ui.common.LocalFullScreenMode
import com.kaleedtc.nitterium.ui.common.viewModelFactory
import com.kaleedtc.nitterium.ui.theme.NitteriumTheme
import com.kaleedtc.nitterium.ui.feature.whatsnew.WhatsNewScreen

class MainActivity : ComponentActivity() {

    private val intentUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intentUrl.value = intent?.dataString

        val app = application as NitteriumApplication
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val viewModel: MainViewModel by viewModels {
            viewModelFactory {
                MainViewModel(app.userPreferencesRepository, packageInfo.longVersionCode)
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val uriHandler = LocalUriHandler.current
            
            if (uiState.isLoading) {
                return@setContent
            }

            val fullScreenMode = remember { mutableStateOf(false) }

            // Handle System Bars visibility
            LaunchedEffect(fullScreenMode.value) {
                val window = this@MainActivity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (fullScreenMode.value) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            val darkTheme = when (uiState.isDarkTheme) {
                true -> true
                false -> false
                null -> isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalFullScreenMode provides fullScreenMode) {
                NitteriumTheme(
                    darkTheme = darkTheme,
                    dynamicColor = uiState.isDynamicColor,
                    trueBlack = uiState.isTrueBlack
                ) {
                    if (uiState.showReleaseNotes) {
                        WhatsNewScreen(
                            versionName = packageInfo.versionName.orEmpty(),
                            isSaving = uiState.isSavingReleaseNotes,
                            saveFailed = uiState.releaseNotesSaveFailed,
                            onContinue = viewModel::dismissReleaseNotes,
                            onDonate = { uriHandler.openUri(getString(R.string.donate_url)) }
                        )
                    } else {
                        NitteriumApp(
                            app = app,
                            isDarkTheme = darkTheme,
                            initialIntentUrl = intentUrl.value,
                            onIntentHandled = { intentUrl.value = null },
                            showNavLabels = uiState.showNavLabels,
                            defaultTab = uiState.defaultTab
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intentUrl.value = intent.dataString
    }
}
