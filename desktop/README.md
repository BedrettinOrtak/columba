# Columba Desktop

Columba için masaüstü (Linux/Windows) uygulaması. Android uygulaması ile tam uyumlu çalışır, aynı veritabanı yapısını kullanır.

## Durum

| Özellik | Durum |
|---------|--------|
| SQLite Veritabanı | ✅ Tamamlandı |
| Identity Yönetimi | ✅ Tamamlandı |
| Conversation Yönetimi | ✅ Tamamlandı |
| Message Yönetimi | ✅ Tamamlandı |
| Compose UI | ✅ Tamamlandı |
| Reticulum Entegrasyonu | ⏳ Planlanıyor |
| Android Uyumu | ⏳ Geliştiriliyor |

## Mimari

```
columba/
├── shared/              # Platform bağımsız domain modelleri ve repository arayüzleri
│   └── domain/
│       ├── model/       # Message, Conversation, Identity
│       └── repository/  # ConversationRepository, IdentityRepository
├── desktop-data/        # Desktop-specific veri katmanı
│   └── db/
│       ├── entity/      # Desktop entity'leri
│       ├── dao/         # SQLite JDBC DAO'ları
│       └── repository/  # Desktop repository implementasyonları
└── desktop/             # Compose Desktop UI
    └── ui/
        ├── screens/     # MainScreen, MessagingScreen, SettingsScreen
        ├── viewmodel/  # MessagingViewModel, SettingsViewModel
        └── theme/       # Material3 tema
```

## Veritabanı Uyumu

Desktop uygulaması, Android uygulamasıyla aynı veritabanı şemasını kullanır:

- `local_identities`: Kullanıcı identiteleri
- `conversations`: Mesajlaşma konuşmaları
- `messages`: Mesajlar

Bu sayede Android'den alınan veritabanı dosyasını masaüstünde kullanmak mümkündür.

## Geliştirme

```bash
# Çalıştırma
./scripts/run-desktop.sh

# Build alma
./scripts/build-desktop.sh

# Gradle ile
./gradlew :desktop:run
./gradlew :desktop:createDistributable
```

Build çıktısı: `desktop/build/compose/binaries/main/app/desktop/bin/desktop`

## Veritabanı Konumu

- Linux: `~/.columba/data/columba.db`
- Windows: `C:\Users\<user>\.columba\data\columba.db`

## Android ile Uyumluluk Planı

1. **Paylaşılan Veritabanı**: Aynı SQLite dosyası her iki platformda da çalışabilir
2. **Identity Senkronizasyon**: Identity'ler export/import edilebilir
3. **Mesaj Geçmişi**: Android'den alınan backup masaüstüne yüklenebilir

## Sonraki Adımlar

- [ ] Reticulum servis entegrasyonu
- [ ] TCP/UDP arayüz desteği
- [ ] Mesaj gönderme/alma (LXMF)
- [ ] Gerçek zamanlı Flow implementasyonu
- [ ] Android ile doğrudan veritabanı paylaşımı
