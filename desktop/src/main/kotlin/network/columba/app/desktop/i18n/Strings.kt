package network.columba.app.desktop.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * String resources for multiple languages.
 */
sealed class Strings {
    abstract val appName: String
    abstract val mainTab: String
    abstract val messagesTab: String
    abstract val settingsTab: String

    abstract val onlineStatus: String
    abstract val offlineStatus: String
    abstract val connectedToMesh: String
    abstract val noActiveConnections: String

    abstract val identityStatus: String
    abstract val identityConfigured: String
    abstract val noIdentityConfigured: String
    abstract val createIdentity: String

    abstract val peers: String
    abstract val messages: String
    abstract val interfaces: String
    abstract val recentActivity: String
    abstract val noRecentActivity: String

    abstract val newConversation: String
    abstract val noConversations: String
    abstract val selectConversation: String
    abstract val typeMessage: String
    abstract val send: String
    abstract val delete: String
    abstract val unread: String

    abstract val yourIdentities: String
    abstract val identitiesConfigured: String
    abstract val new: String
    abstract val create: String
    abstract val edit: String
    abstract val activate: String
    abstract val cancel: String
    abstract val save: String
    abstract val displayName: String
    abstract val identityHash: String

    abstract val reticulumService: String
    abstract val reticulumEnabled: String
    abstract val autoAnnounce: String
    abstract val tcpInterface: String
    abstract val connectViaTcp: String
    abstract val i2pInterface: String
    abstract val connectViaI2p: String

    abstract val about: String
    abstract val version: String
    abstract val appDescription: String

    abstract val justNow: String
    abstract val minutesAgo: String
    abstract val hoursAgo: String
    abstract val daysAgo: String

    abstract val statusSent: String
    abstract val statusDelivered: String
    abstract val statusPending: String
    abstract val statusFailed: String

    object Turkish : Strings() {
        override val appName = "Columba"
        override val mainTab = "Ana"
        override val messagesTab = "Mesajlar"
        override val settingsTab = "Ayarlar"

        override val onlineStatus = "Reticulum Çevrimiçi"
        override val offlineStatus = "Reticulum Çevrimdışı"
        override val connectedToMesh = "Mesh ağına bağlı"
        override val noActiveConnections = "Aktif bağlantı yok"

        override val identityStatus = "Kimlik Durumu"
        override val identityConfigured = "✓ Kimlik yapılandırıldı"
        override val noIdentityConfigured = "⚠ Kimlik yapılandırılmadı. Ayarlardan oluşturun."
        override val createIdentity = "Kimlik Oluştur"

        override val peers = "Kullanıcılar"
        override val messages = "Mesajlar"
        override val interfaces = "Arayüzler"
        override val recentActivity = "Son Aktivite"
        override val noRecentActivity = "Son aktivite yok"

        override val newConversation = "Yeni Konuşma"
        override val noConversations = "Henüz konuşma yok"
        override val selectConversation = "Mesajlaşmak için bir konuşma seçin"
        override val typeMessage = "Bir mesaj yazın..."
        override val send = "Gönder"
        override val delete = "Sil"
        override val unread = "okunmamış"

        override val yourIdentities = "Kimlikleriniz"
        override val identitiesConfigured = "%d kimlik yapılandırıldı"
        override val new = "Yeni"
        override val create = "Oluştur"
        override val edit = "Düzenle"
        override val activate = "Etkinleştir"
        override val cancel = "İptal"
        override val save = "Kaydet"
        override val displayName = "Görünen Ad"
        override val identityHash = "Kimlik Hash"

        override val reticulumService = "Reticulum Servisi"
        override val reticulumEnabled = "Reticulum ağ bağlantısını etkinleştir"
        override val autoAnnounce = "Ağda otomatik duyuru yap"
        override val tcpInterface = "TCP Arayüzü"
        override val connectViaTcp = "TCP ile bağlan (varsayılan: 4242)"
        override val i2pInterface = "I2P Arayüzü"
        override val connectViaI2p = "I2P ağı ile bağlan"

        override val about = "Hakkında"
        override val version = "Sürüm"
        override val appDescription = "Reticulum ağı için bir mesajlaşma uygulaması."

        override val justNow = "Az önce"
        override val minutesAgo = "%d dk önce"
        override val hoursAgo = "%d saat önce"
        override val daysAgo = "%d gün önce"

        override val statusSent = "gönderildi"
        override val statusDelivered = "iletildi"
        override val statusPending = "bekliyor"
        override val statusFailed = "başarısız"
    }

    object Kurdish : Strings() {
        override val appName = "Columba"
        override val mainTab = "Sere"
        override val messagesTab = "Peyam"
        override val settingsTab = "Sazkarî"

        override val onlineStatus = "Reticulum Serhêde"
        override val offlineStatus = "Reticulum Serne"
        override val connectedToMesh = "Pêwendî tora kirî"
        override val noActiveConnections = "Pêwendî çalak tune"

        override val identityStatus = "Rewşa Niyşan"
        override val identityConfigured = "✓ Niyşan saz kirî"
        override val noIdentityConfigured = "⚠ Niyşan saz nekirî. Di sazkarî çêkirin."
        override val createIdentity = "Niyşan Çêkirin"

        override val peers = "Bikarhêner"
        override val messages = "Peyam"
        override val interfaces = "Rûpel"
        override val recentActivity = "Çalakiya Dawî"
        override val noRecentActivity = "Çalakiya dawî tune"

        override val newConversation = "Gotûbêja Nû"
        override val noConversations = "Hêj gotûbêj tune"
        override val selectConversation = "Ji bo peyamandinê gotûbêjê hilbijêrin"
        override val typeMessage = "Peyamek binivîse..."
        override val send = "Bişîne"
        override val delete = "Jê bibe"
        override val unread = "nexwendî"

        override val yourIdentities = "Niyşanên We"
        override val identitiesConfigured = "%d niyşan saz kirî"
        override val new = "Nû"
        override val create = "Çêkirin"
        override val edit = "Biguherîne"
        override val activate = "Çalak bike"
        override val cancel = "Betal bike"
        override val save = "Tomar bike"
        override val displayName = "Nava Nîşan"
        override val identityHash = "Hasha Niyşan"

        override val reticulumService = "Xizmeta Reticulum"
        override val reticulumEnabled = "Pêwendî tora Reticulum çalak bike"
        override val autoAnnounce = "Di tora de xwe ragihandin bike"
        override val tcpInterface = "Rûpela TCP"
        override val connectViaTcp = "Bi TCP re pêwendî be (werî: 4242)"
        override val i2pInterface = "Rûpela I2P"
        override val connectViaI2p = "Bi tora I2P re pêwendî be"

        override val about = "Derheq"
        override val version = "Guherto"
        override val appDescription = "Sepeta peyamandinê ji bo tora Reticulum."

        override val justNow = "niha"
        override val minutesAgo = "%d deq berê"
        override val hoursAgo = "%d saet berê"
        override val daysAgo = "%d roj berê"

        override val statusSent = "şandî"
        override val statusDelivered = "gihandî"
        override val statusPending = "dixwîn"
        override val statusFailed = "têk çû"
    }

    object Farsi : Strings() {
        override val appName = "کلمبا"
        override val mainTab = "اصلی"
        override val messagesTab = "پیام"
        override val settingsTab = "تنظیمات"

        override val onlineStatus = "ریتیکولوم آنلاین"
        override val offlineStatus = "ریتیکولوم آفلاین"
        override val connectedToMesh = "متصل به مش"
        override val noActiveConnections = "بدون اتصال فعال"

        override val identityStatus = "حالة الهوية"
        override val identityConfigured = "✓ الهوية مهیأة"
        override val noIdentityConfigured = "⚠ الهوية غیر مهیأة. قم بإنشائها در تنظیمات."
        override val createIdentity = "ایجاد هویت"

        override val peers = "همکاران"
        override val messages = "پیام‌ها"
        override val interfaces = "رابطه‌ها"
        override val recentActivity = "فعالیت اخیر"
        override val noRecentActivity = "فعالیت اخیری وجود ندارد"

        override val newConversation = "محادثه جدید"
        override val noConversations = "هنوز مکالمه‌ای وجود ندارد"
        override val selectConversation = "یک مکالمه برای پیام‌رسانی انتخاب کنید"
        override val typeMessage = "پیامی تایپ کنید..."
        override val send = "ارسال"
        override val delete = "حذف"
        override val unread = "خوانده نشده"

        override val yourIdentities = "هوئیت‌های شما"
        override val identitiesConfigured = "%d هویت مهیأة شده"
        override val new = "جدید"
        override val create = "ایجاد"
        override val edit = "ویرایش"
        override val activate = "فعال‌سازی"
        override val cancel = "لغو"
        override val save = "ذخیره"
        override val displayName = "نام نمایشی"
        override val identityHash = "هش هویت"

        override val reticulumService = "سرویس رتیکولوم"
        override val reticulumEnabled = "فعال‌سازی اتصال شبکه رتیکولوم"
        override val autoAnnounce = "اعلان خودکار در شبکه"
        override val tcpInterface = "رابطه TCP"
        override val connectViaTcp = "اتصال از طریق TCP (الافتراضي: 4242)"
        override val i2pInterface = "رابطه I2P"
        override val connectViaI2p = "اتصال از طریق شبکه I2P"

        override val about = "درباره"
        override val version = "النسخه"
        override val appDescription = "یک برنامه پیام‌رسانی برای شبکه رتیکولوم."

        override val justNow = "همین الان"
        override val minutesAgo = "%d دقیقه پیش"
        override val hoursAgo = "%d ساعت پیش"
        override val daysAgo = "%d روز پیش"

        override val statusSent = "ارسال شد"
        override val statusDelivered = "تحویل داده شد"
        override val statusPending = "در انتظار"
        override val statusFailed = "ناموفق"
    }

    object Arabic : Strings() {
        override val appName = "كولومبا"
        override val mainTab = "الرئيسية"
        override val messagesTab = "الرسائل"
        override val settingsTab = "الإعدادات"

        override val onlineStatus = "ريتيكولوم متصل"
        override val offlineStatus = "ريتيكولوم غير متصل"
        override val connectedToMesh = "متصل بالشبكة"
        override val noActiveConnections = "لا توجد اتصالات نشطة"

        override val identityStatus = "حالة الهوية"
        override val identityConfigured = "✓ الهوية مهيأة"
        override val noIdentityConfigured = "⚠ الهوية غير مهيأة. قم بإنشائها في الإعدادات."
        override val createIdentity = "إنشاء هوية"

        override val peers = "الأقران"
        override val messages = "الرسائل"
        override val interfaces = "الواجهات"
        override val recentActivity = "النشاط الأخير"
        override val noRecentActivity = "لا يوجد نشاط أخير"

        override val newConversation = "محادثة جديدة"
        override val noConversations = "لا توجد محادثات بعد"
        override val selectConversation = "اختر محادثة لبدء المراسلة"
        override val typeMessage = "اكتب رسالة..."
        override val send = "إرسال"
        override val delete = "حذف"
        override val unread = "غير مقروء"

        override val yourIdentities = "هوياتك"
        override val identitiesConfigured = "%d هوية مهيأة"
        override val new = "جديد"
        override val create = "إنشاء"
        override val edit = "تعديل"
        override val activate = "تفعيل"
        override val cancel = "إلغاء"
        override val save = "حفظ"
        override val displayName = "الاسم المعروض"
        override val identityHash = "تجزئة الهوية"

        override val reticulumService = "خدمة رتيكولوم"
        override val reticulumEnabled = "تفعيل اتصال شبكة رتيكولوم"
        override val autoAnnounce = "إعلان تلقائي في الشبكة"
        override val tcpInterface = "واجهة TCP"
        override val connectViaTcp = "الاتصال عبر TCP (الافتراضي: 4242)"
        override val i2pInterface = "واجهة I2P"
        override val connectViaI2p = "الاتصال عبر شبكة I2P"

        override val about = "حول"
        override val version = "الإصدار"
        override val appDescription = "تطبيق مراسلة لشبكة رتيكولوم."

        override val justNow = "الآن"
        override val minutesAgo = "منذ %d دقيقة"
        override val hoursAgo = "منذ %d ساعة"
        override val daysAgo = "منذ %d يوم"

        override val statusSent = "أُرسل"
        override val statusDelivered = "تم التسليم"
        override val statusPending = "قيد الانتظار"
        override val statusFailed = "فشل"
    }

    companion object {
        private var currentLanguage: Language = Language.TURKISH
        private var currentStrings: Strings = Turkish

        // State for recomposition
        private val _stringsState = MutableStateFlow(currentStrings)
        val stringsState: StateFlow<Strings> = _stringsState.asStateFlow()

        fun setLanguage(language: Language) {
            currentLanguage = language
            currentStrings = when (language) {
                Language.TURKISH -> Turkish
                Language.KURDISH -> Kurdish
                Language.FARSI -> Farsi
                Language.ARABIC -> Arabic
                Language.ENGLISH -> Turkish // Fallback
            }
            _stringsState.value = currentStrings
        }

        fun get(): Strings = _stringsState.value
        fun getLanguage(): Language = currentLanguage

        fun formatCount(string: String, count: Int): String {
            return string.format(count)
        }

        fun formatTimeAgo(string: String, value: Int): String {
            return string.format(value)
        }
    }
}
