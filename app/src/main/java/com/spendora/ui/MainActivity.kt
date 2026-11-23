package com.spendora.ui

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
import com.spendora.ui.screens.BackupScreen
import com.spendora.ui.screens.BudgetScreen
import com.spendora.ui.screens.DashboardScreen
import com.spendora.ui.screens.InsightsScreen
import com.spendora.ui.screens.OnboardingScreen
import com.spendora.ui.screens.SettingsScreen
import com.spendora.ui.screens.SmsTesterScreen
import com.spendora.ui.screens.SplitsScreen
import com.spendora.ui.theme.SpendoraTheme

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
    val dashboard by viewModel.dashboard.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val shares by viewModel.shares.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()
    val smsResult by viewModel.smsTestResult.collectAsState()

    var destination by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD) }
    var testerSender by rememberSaveable { mutableStateOf("VM-HDFCBK") }
    var testerBody by rememberSaveable { mutableStateOf("") }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }

    val name = userName
    when {
        name == null -> Unit // Still reading the saved name; keep the blank surface.
        name.isBlank() -> OnboardingScreen(onContinue = { viewModel.setUserName(it) })
        else -> {
            if (destination != AppDestination.DASHBOARD) {
                BackHandler { destination = AppDestination.DASHBOARD }
            }
            when (destination) {
                AppDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    userName = name,
                    resumeTick = resumeTick,
                    onNavigate = { destination = it }
                )

                AppDestination.INSIGHTS -> InsightsScreen(
                    expenses = allExpenses.filter { it.status == com.spendora.data.Expense.STATUS_SAVED },
                    onBack = { destination = AppDestination.DASHBOARD }
                )

                AppDestination.BUDGET -> BudgetScreen(
                    month = dashboard.month.toString(),
                    limit = dashboard.budgetLimit,
                    monthTotal = dashboard.monthTotal,
                    onSave = { viewModel.setBudget(dashboard.month, it) },
                    onBack = { destination = AppDestination.DASHBOARD }
                )

                AppDestination.BACKUP -> BackupScreen(
                    onExport = { viewModel.exportBackup(it, backupPassphrase) },
                    onImport = { viewModel.importBackup(it, backupPassphrase) },
                    onBack = { destination = AppDestination.DASHBOARD },
                    lastResult = backupResult,
                    passphrase = backupPassphrase,
                    onPassphrase = { backupPassphrase = it }
                )

                AppDestination.SPLITS -> SplitsScreen(
                    shares = shares,
                    expenses = allExpenses,
                    onToggle = { viewModel.toggleShare(it) },
                    onAdd = { id, person, amount -> viewModel.addShare(id, person, amount) },
                    onBack = { destination = AppDestination.DASHBOARD }
                )

                AppDestination.SMS_TESTER -> SmsTesterScreen(
                    sender = testerSender,
                    body = testerBody,
                    onSender = { testerSender = it },
                    onBody = { testerBody = it },
                    onTest = { viewModel.testSmsRule(testerSender, testerBody) },
                    result = smsResult,
                    onBack = { destination = AppDestination.SETTINGS }
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    userName = name,
                    resumeTick = resumeTick,
                    onBack = { destination = AppDestination.DASHBOARD },
                    onOpenSmsTester = { destination = AppDestination.SMS_TESTER }
                )
            }
        }
    }
}
