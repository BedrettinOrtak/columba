package network.columba.app.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import network.columba.app.desktop.i18n.Language
import network.columba.app.desktop.i18n.Strings
import network.columba.app.desktop.ui.screens.MainScreen
import network.columba.app.desktop.ui.screens.MessagingScreen
import network.columba.app.desktop.ui.screens.SettingsScreen
import network.columba.app.desktop.ui.theme.ColumbaTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import network.columba.desktop.data.di.desktopDataModule
import network.columba.desktop.data.preference.AppPreferences
import androidx.compose.runtime.getValue

// State for language
class AppState {
    private val _currentLanguage = mutableStateOf(Language.TURKISH)
    val currentLanguage: State<Language> = _currentLanguage

    fun setLanguage(language: Language) {
        _currentLanguage.value = language
        Strings.setLanguage(language)
    }
}

val appState = AppState()

@Composable
fun App() {
    val currentLanguage by appState.currentLanguage
    val strings by Strings.stringsState.collectAsState()

    // Support RTL for Arabic and Farsi
    val layoutDirection = if (currentLanguage == Language.ARABIC || currentLanguage == Language.FARSI) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ColumbaTheme {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = false,
                            onClick = { /* Handled by state */ },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = strings.mainTab
                                )
                            },
                            label = { Text(strings.mainTab) }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { /* Handled by state */ },
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Message,
                                    contentDescription = strings.messagesTab
                                )
                            },
                            label = { Text(strings.messagesTab) }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { /* Handled by state */ },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = strings.settingsTab
                                )
                            },
                            label = { Text(strings.settingsTab) }
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Main content with state management
                    MainScreenContent(strings, appState = appState)
                }
            }
        }
    }
}

@Composable
private fun MainScreenContent(strings: Strings, appState: AppState) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab content
        when (selectedTab) {
            0 -> MainScreen(modifier = Modifier.fillMaxSize())
            1 -> MessagingScreen(modifier = Modifier.fillMaxSize())
            2 -> SettingsScreen(modifier = Modifier.fillMaxSize(), appState = appState)
        }
    }
}

fun main() = application {
    // Initialize Koin
    startKoin {
        modules(desktopDataModule)
    }

    // Load saved language preference
    val preferences = AppPreferences.getInstance()
    val savedLanguage = preferences.getLanguage()
    appState.setLanguage(Language.fromCode(savedLanguage))

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    Window(
        onCloseRequest = {
            stopKoin()
            exitApplication()
        },
        title = "Columba Desktop",
        state = windowState,
        icon = null // TODO: Add window icon
    ) {
        App()
    }
}
