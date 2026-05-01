package network.columba.app.desktop.i18n

enum class Language(val code: String, val nativeName: String, val flag: String) {
    TURKISH("tr", "Türkçe", "🇹🇷"),
    KURDISH("ku", "Kurdî", "🇹🇯"),
    FARSI("fa", "فارسی", "🇮🇷"),
    ARABIC("ar", "العربية", "🇸🇦"),
    ENGLISH("en", "English", "🇬🇧"); // Varsayılan

    companion object {
        fun fromCode(code: String): Language = values().find { it.code == code } ?: ENGLISH
        fun getAll(): List<Language> = listOf(TURKISH, KURDISH, FARSI, ARABIC)
    }
}
