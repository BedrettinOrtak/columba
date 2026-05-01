package network.columba.desktop.data.preference

import org.slf4j.LoggerFactory
import java.util.Properties
import java.io.File

/**
 * Simple preferences manager for desktop app.
 * Stores settings in a properties file.
 */
class AppPreferences(private val configDir: File) {
    private val logger = LoggerFactory.getLogger(AppPreferences::class.java)
    private val propertiesFile = File(configDir, "app.properties")
    private val properties = Properties()

    init {
        load()
    }

    private fun load() {
        if (propertiesFile.exists()) {
            try {
                propertiesFile.inputStream().use { properties.load(it) }
                logger.info("Preferences loaded from ${propertiesFile.absolutePath}")
            } catch (e: Exception) {
                logger.error("Failed to load preferences", e)
            }
        }
    }

    private fun save() {
        try {
            propertiesFile.parentFile?.mkdirs()
            propertiesFile.outputStream().use { properties.store(it, "Columba Desktop Preferences") }
            logger.debug("Preferences saved to ${propertiesFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to save preferences", e)
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return properties.getProperty(key, defaultValue)
    }

    fun setString(key: String, value: String) {
        properties.setProperty(key, value)
        save()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return properties.getProperty(key)?.toIntOrNull() ?: defaultValue
    }

    fun setInt(key: String, value: Int) {
        properties.setProperty(key, value.toString())
        save()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return properties.getProperty(key)?.toBoolean() ?: defaultValue
    }

    fun setBoolean(key: String, value: Boolean) {
        properties.setProperty(key, value.toString())
        save()
    }

    fun getLanguage(): String {
        return getString("language", "tr")
    }

    fun setLanguage(languageCode: String) {
        setString("language", languageCode)
    }

    companion object {
        private var instance: AppPreferences? = null

        fun getInstance(configDir: File = File(System.getProperty("user.home"), ".columba")): AppPreferences {
            if (instance == null) {
                instance = AppPreferences(configDir)
            }
            return instance!!
        }
    }
}
