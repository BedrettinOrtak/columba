package network.columba.desktop.data.di

import network.columba.desktop.data.db.ColumbaDatabase
import network.columba.desktop.data.preference.AppPreferences
import network.columba.desktop.data.repository.DesktopConversationRepository
import network.columba.desktop.data.repository.DesktopIdentityRepository
import network.columba.desktop.data.reticulum.DesktopReticulumService
import network.columba.shared.domain.repository.ConversationRepository
import network.columba.shared.domain.repository.IdentityRepository
import org.koin.dsl.module
import java.io.File

/**
 * Dependency injection module for desktop data layer.
 * Uses Koin for simple DI without Android's Hilt.
 */
val desktopDataModule = module {
    // Config directory
    single<File> {
        File(System.getProperty("user.home"), ".columba").apply { mkdirs() }
    }

    // Preferences
    single { AppPreferences.getInstance(get()) }

    // Database singleton
    single<ColumbaDatabase> {
        ColumbaDatabase.getInstance(get())
    }

    // Reticulum networking service. Held as a singleton so identity, router,
    // and interfaces all live for the lifetime of the desktop process.
    single { DesktopReticulumService(get()) }

    // Repositories
    single<ConversationRepository> {
        DesktopConversationRepository(get(), get())
    }

    single<IdentityRepository> {
        DesktopIdentityRepository(get(), get())
    }
}

/**
 * Initialize the desktop data module.
 * Call this at application startup.
 */
fun initDataModule() {
    // Koin will be initialized in the DesktopApplication
}
