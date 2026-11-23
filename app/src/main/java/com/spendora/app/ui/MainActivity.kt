package com.spendora.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendora.app.ui.screens.DashboardScreen
import com.spendora.app.ui.screens.OnboardingScreen
import com.spendora.app.ui.screens.SettingsScreen
import com.spendora.app.ui.theme.SpendoraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Bumped on every resume so permission statuses on screen are re-read live. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpendoraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(viewModel = viewModel, resumeTick = resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        viewModel.onAppResumed()
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel, resumeTick: Int) {
    val userName by viewModel.userName.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val name = userName
    when {
        name == null -> Unit // Still reading the saved name; keep the blank surface.
        name.isBlank() -> OnboardingScreen(onContinue = { viewModel.setUserName(it) })
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(
                viewModel = viewModel,
                userName = name,
                resumeTick = resumeTick,
                onBack = { showSettings = false }
            )
        }
        else -> DashboardScreen(
            viewModel = viewModel,
            userName = name,
            resumeTick = resumeTick,
            onOpenSettings = { showSettings = true }
        )
    }
}
