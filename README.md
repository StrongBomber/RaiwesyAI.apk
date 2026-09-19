# Raiwesy AI 🤖

**NVIDIA API + GLM-5.3 modeli ile çalışan, modern Material Design 3 sohbet uygulaması.**

Kullanıcı bir metin yazar, uygulama NVIDIA integrate API üzerinden `z-ai/glm-5.3` modeline istek gönderir ve yapay zekanın yanıtını **token token (streaming)** olarak ekranda gösterir.

| | |
|---|---|
| **Dil** | Kotlin |
| **UI** | Jetpack Compose + Material Design 3 |
| **Mimari** | MVVM (ViewModel + Repository + Dependency Container) |
| **Ağ** | Retrofit 2 + OkHttp 4 (SSE streaming) |
| **Tema** | Açık / Koyu / Sistem + Android 12+ dinamik renkler |
| **minSdk / targetSdk** | 26 / 35 |
| **CI** | GitHub Actions → Debug + Release APK artifact'ları |

---

## ✨ Özellikler

- 💬 **Sohbet ekranı** — kullanıcı (indigo) ve AI (yüzey tonu) balonları, avatar, zaman damgası
- 📜 **Mesaj geçmişi** — uygulama kapansa bile kalıcı (cihazda saklanır) + son 8 mesaj API'ye bağlam olarak gönderilir
- 📋 **Kopyala butonu** — her mesajda, tek dokunuşla panoya kopyalar
- 🗑️ **Mesaj silme** — onay diyaloğuyla tekil silme + "tüm sohbeti temizle"
- 🌙 **Karanlık tema** — Sistem / Açık / Koyu seçimi (ayarlar alt sayfasından)
- ⏳ **Yükleniyor animasyonu** — "düşünüyor" noktacıkları + streaming sırasında yanıp sönen imleç
- 🛑 **Durdur butonu** — devam eden yanıtı durdurur (kısmi yanıt korunur)
- 📶 **İnternet kontrolü** — `NetworkMonitor` canlı bağlantı izler, çevrimdışı bandı gösterir
- 🚨 **Hata yakalama** — 401/403/404/429/5xx, zaman aşımı, bağlantı hatası → Türkçe, anlaşılır mesaj + **"Tekrar dene"**
- 🛡️ **Crash önleme** — global `UncaughtExceptionHandler`, tüm ağ/JSON işlemleri try-catch, dostane yeniden başlatma diyaloğu
- 🔑 **Güvenli API anahtarı** — kaynak koda **asla gömülmez** (aşağıda anlatılır)
- 🇹🇷 **Türkçe arayüz**

---

## 🏗️ Mimari (MVVM)

```
┌─────────────────────────────── UI (Compose) ───────────────────────────────┐
│  ChatScreen  •  MessageBubble  •  Composer  •  EmptyState  •  Banners      │
│  SettingsSheet                                                        │
└──────────────▲───────────────────────────────────────────┬─────────────────┘
               │  StateFlow<ChatUiState> (immutable)       │  user intents
┌──────────────┴───────────────────────────────────────────▼─────────────────┐
│                         ChatViewModel  (ViewModel)                          │
│   state yönetimi • hata haritalama • gönder/durdur/tekrar dene              │
└──────────────▲───────────────────────────────────────────┬─────────────────┘
               │  Flow<ChatStreamEvent>                    │  List<ChatMessage>
┌──────────────┴───────────────────────────────────────────▼─────────────────┐
│      ChatRepository (Data)   •   MessageStore   •   ApiKeyManager           │
│      NvidiaApi (Retrofit)    •   NetworkMonitor                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                        https://integrate.api.nvidia.com/v1/
                                   │
                            model: z-ai/glm-5.3
```

### Proje yapısı

```
RaiwesyAI/
├── .github/
│   ├── workflows/android.yml        # CI: otomatik debug+release APK
│   └── dependabot.yml               # haftalık bağımlılık güncellemeleri
├── app/
│   ├── build.gradle.kts             # modül yapılandırması + anahtar enjeksiyonu
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/raiwesy/ai/
│       │   │   ├── RaiwesyApp.kt            # Application + CrashHandler
│       │   │   ├── MainActivity.kt          # tek activity (Compose kökü)
│       │   │   ├── core/network/            # NvidiaApi, NetworkMonitor, hatalar
│       │   │   ├── core/util/               # ApiKeyManager, MessageStore, CrashHandler
│       │   │   ├── data/                    # ChatRepository, modeller, ChatMessage
│       │   │   ├── di/AppContainer.kt       # Retrofit/OkHttp kurulumu (DI)
│       │   │   └── ui/                      # ChatScreen, ChatViewModel, theme, bileşenler
│       │   └── res/                         # stringler, temalar, launcher ikonları
│       └── test/java/com/raiwesy/ai/data/  # birim testler (SSE ayrıştırma vb.)
├── gradle/
│   ├── libs.versions.toml           # 📦 versioning: tüm sürümler tek yerde
│   └── wrapper/
├── .gitignore
├── local.properties.example
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🔑 API Anahtarı Yönetimi

**Anahtar kaynak kodda hiçbir yerde YOK.** Üç kademeli sistem:

| Öncelik | Kaynak | Kullanım |
|---|---|---|
| 1 | `local.properties` → `nvidia_api_key` | Yerel geliştirme |
| 2 | `NVIDIA_API_KEY` ortam değişkeni / GitHub Secret | CI ve script'ler |
| 3 | **Uygulama içi Ayarlar ekranı** | Uygulama kullanıcısı (cihazda **şifreli** saklanır, gömülü anahtarı ezer) |

### Yerel derleme

```bash
cp local.properties.example local.properties
# local.properties içine yazın:
#   nvidia_api_key=nvda-...
./gradlew assembleDebug
```

### GitHub Actions (CI)

Repo → **Settings → Secrets and variables → Actions** içine şunları ekleyin:

| Secret | Zorunlu | Açıklama |
|---|---|---|
| `NVIDIA_API_KEY` | Evet (uygulamanın çalışması için) | NVIDIA API anahtarı |
| `KEYSTORE_BASE64` | Hayır | Release keystore'u base64 (yoksa APK imzasız üretilir) |
| `KEYSTORE_PASSWORD` | Hayır | Keystore parolası |
| `KEY_ALIAS` | Hayır | Anahtar alias'ı |
| `KEY_PASSWORD` | Hayır | Anahtar parolası |

Keystore'u base64'e çevirme:

```bash
base64 -w0 release.keystore
```

Her `push` sonrası Actions sekmesinden APK'ları indirin:

- **RaiwesyAI-debug-apk** — `app-debug.apk` (imzalı, kuruluma hazır)
- **RaiwesyAI-release-apk** — minify + resource shrink'lı release APK

---

## 🚀 Kurulum ve Derleme

**Gereksinimler:** Android Studio (Koala veya üzeri) • JDK 17 • Android SDK 35

```bash
git clone <repo-url>
cd RaiwesyAI.apk
cp local.properties.example local.properties   # anahtarınızı girin
./gradlew assembleDebug                        # ya da Android Studio'dan Run
```

Android Studio: projeyi açın, bağımlılıklar senkronize olsun, `Run ▶`.

---

## 🧪 Testler

```bash
./gradlew test            # JVM birim testleri (SSE ayrıştırma, hata çıkarımı)
```

---

## 📡 API Kullanımı

- **Base URL:** `https://integrate.api.nvidia.com/v1/`
- **Model:** `z-ai/glm-5.3`
- **Endpoint:** `POST /chat/completions` (OpenAI uyumlu, `stream: true` → SSE)

```
POST /v1/chat/completions
Authorization: Bearer <anahtar>

{
  "model": "z-ai/glm-5.3",
  "messages": [
    {"role": "system", "content": "Sen Raiwesy AI'sın..."},
    {"role": "user", "content": "Merhaba"}
  ],
  "stream": true,
  "temperature": 0.7,
  "max_tokens": 1024
}
```

Her `data: {...}` satırı ayrıştırılır, `choices[0].delta.content` balona eklenir, `data: [DONE]` geldiğinde mesaj tamamlanır.

> **Not:** Model adı, `app/build.gradle.kts` içindeki `MODEL` BuildConfig alanından değiştirilebilir.

---

## 📈 Versioning

- **Uygulama sürümü:** `app/build.gradle.kts` → `versionCode` / `versionName` (semantik sürüm: `MAJOR.MINOR.PATCH`)
- **Bağımlılık sürümleri:** `gradle/libs.versions.toml` (Gradle Version Catalog) — tek dosyadan yönetilir
- **GitHub Actions** ve **AGP/Gradle** sürümleri de aynı kataloğa bağlıdır

Yeni sürüm için: `versionName` + `versionCode` artırın → commit → CI otomatik APK üretir.

---

## 🔒 Güvenlik

- API anahtarı **yalnızca** derleme zamanında `BuildConfig`'a girer; `local.properties` git'te yok.
- Çalıştırma zamanı anahtarı **EncryptedSharedPreferences** (Android Keystore, AES-256) ile saklanır.
- OkHttp loglama `REDACTED` seviyesinde: `Authorization` başlığı asla log'a yazılmaz (release'da log tamamen kapalı).
- Global crash handler: istisna loglanır, kullanıcı uyarılır, uygulama temiz görevle yeniden başlatılır (döngü korumalı).

---

## 📄 Lisans

Bu proje özel kullanım içindir. NVIDIA, GLM ve ilgili marka adları sahiplerine aittir.
