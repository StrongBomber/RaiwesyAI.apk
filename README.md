# Raiwesy AI 🤖

**Raiwesy AI — modern Material Design 3 sohbet uygulaması.**

Kullanıcı bir metin yazar, uygulama yapay zeka API'sine istek gönderir ve yanıtı **token token (streaming)** olarak ekranda gösterir. Uygulama **hazır anahtarla** gelir; isterseniz kendi anahtarınızı Ayarlar'dan değiştirebilirsiniz.

| | |
|---|---|
| **Dil** | Kotlin |
| **UI** | Jetpack Compose + Material Design 3 |
| **Mimari** | MVVM (ViewModel + Repository + Dependency Container) |
| **Ağ** | Retrofit 2 + OkHttp 4 (SSE streaming) |
| **Tema** | Açık / Koyu / Sistem + Android 12+ dinamik renkler |
| **minSdk / targetSdk** | 26 / 35 |
| **Sürüm** | 1.1.0 (versionCode 2) |
| **CI** | GitHub Actions → Debug + Release APK artifact'ları + API sağlık kontrolü |

---

## ✨ Özellikler

- 💬 **Sohbet ekranı** — kullanıcı (indigo) ve AI (yüzey tonu) balonları, avatar, zaman damgası
- 🗂️ **Çoklu sohbet (ChatGPT gibi)** — istediğiniz kadar ayrı sohbet açın; sohbet listesi (menü ikonu), yeni sohbet, sohbet açma, sohbet silme; başlıklar ilk mesajdan otomatik oluşur
- 📜 **Mesaj geçmişi** — tüm sohbetler uygulama kapansa bile kalıcı (cihazda saklanır) + son 8 mesaj API'ye bağlam olarak gönderilir
- 📋 **Kopyala butonu** — her mesajda, tek dokunuşla panoya kopyalar
- 🗑️ **Silme işlemleri** — tekil mesaj silme, sohbeti temizleme, sohbet silme (onay diyaloğuyla)
- 🌙 **Karanlık tema** — Sistem / Açık / Koyu seçimi (ayarlar alt sayfasından)
- ⏳ **Yükleniyor animasyonu** — "düşünüyor" noktacıkları + streaming sırasında yanıp sönen imleç
- 🛑 **Durdur butonu** — devam eden yanıtı durdurur (kısmi yanıt korunur)
- 📶 **İnternet kontrolü** — `NetworkMonitor` canlı bağlantı izler, çevrimdışı bandı gösterir
- 🚨 **Hata yakalama** — 401/403/404/429/5xx, zaman aşımı, bağlantı hatası → Türkçe, anlaşılır mesaj + **"Tekrar dene"**
- 🛡️ **Crash önleme** — global `UncaughtExceptionHandler`, tüm ağ/JSON işlemleri try-catch, dostane yeniden başlatma diyaloğu
- 🔑 **Otomatik API anahtarı** — uygulama hazır anahtarla çalışır; kendi anahtarınızla değiştirebilirsiniz
- 🇹🇷 **Türkçe arayüz**

---

## 🏗️ Mimari (MVVM)

```
┌─────────────────────────────── UI (Compose) ───────────────────────────────┐
│  ChatScreen  •  ConversationsScreen  •  MessageBubble  •  Composer         │
│  EmptyState  •  Banners  •  SettingsSheet                                  │
└──────────────▲───────────────────────────────────────────┬─────────────────┘
               │  StateFlow<ChatUiState> (immutable)       │  user intents
┌──────────────┴───────────────────────────────────────────▼─────────────────┐
│                         ChatViewModel  (ViewModel)                          │
│   state yönetimi • hata haritalama • sohbet/mesaj işlemleri                │
└──────────────▲───────────────────────────────────────────┬─────────────────┘
               │  Flow<ChatStreamEvent>                    │  Conversation
┌──────────────┴───────────────────────────────────────────▼─────────────────┐
│      ChatRepository (Data)   •   ConversationStore   •   ApiKeyManager      │
│      AiApi (Retrofit)        •   NetworkMonitor                              │
└────────────────────────────────────────────────────────────────────────────┘
                                    │
                        https://integrate.api.nvidia.com/v1/
```

### Proje yapısı

```
RaiwesyAI/
├── .github/
│   ├── workflows/android.yml        # CI: otomatik debug+release APK + API kontrolü
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
│       │   │   ├── core/network/            # AiApi, NetworkMonitor, hatalar
│       │   │   ├── core/util/               # ApiKeyManager, ConversationStore, CrashHandler
│       │   │   ├── data/                    # ChatRepository, Conversation, modeller
│       │   │   ├── di/AppContainer.kt       # Retrofit/OkHttp kurulumu (DI)
│       │   │   └── ui/                      # ChatScreen, ConversationsScreen, ChatViewModel
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

Dört kademeli sistem — **uygulama hazır anahtarla açılır, çalışır:**

| Öncelik | Kaynak | Kullanım |
|---|---|---|
| 1 | Uygulama içi Ayarlar ekranı | Kullanıcı kendi anahtarını girer (cihazda **şifreli** saklanır) |
| 2 | `local.properties` → `nvidia_api_key` | Yerel geliştirme |
| 3 | `NVIDIA_API_KEY` ortam değişkeni / GitHub Secret | CI derlemesi (APK'ya gömülür) |
| 4 | **Gömülü varsayılan anahtar** (`ApiKeyManager.DEFAULT_API_KEY`) | Uygulama kutudan çıktığı gibi çalışır |

> **Not:** Gömülü anahtar APK içinde bulunur (APK'lar decompile edilebilir).
> Üretimde kendi anahtarınızı GitHub Secrets'a ekleyip gömülü anahtarı
> `ApiKeyManager` içinden kaldırmanız önerilir.

### Yerel derleme

```bash
cp local.properties.example local.properties
# (isteğe bağlı) local.properties içine yazın:
#   nvidia_api_key=<kendi-anahtarınız>
./gradlew assembleDebug
```

### GitHub Actions (CI)

Repo → **Settings → Secrets and variables → Actions** içine şunları ekleyin:

| Secret | Zorunlu | Açıklama |
|---|---|---|
| `NVIDIA_API_KEY` | Hayır (opsiyonel) | CI derlemesine gömülecek API anahtarı (yoksa gömülü varsayılan kullanılır) |
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
- **build-output-log** — yalnızca derleme hatasında tam Gradle logu

Ek olarak **API Health Check** işi, API anahtarının + model adının çalıştığını doğrular
(secret tanımlı değilse atlar).

---

## 🚀 Kurulum ve Derleme

**Gereksinimler:** Android Studio (Koala veya üzeri) • JDK 17 • Android SDK 35

```bash
git clone <repo-url>
cd RaiwesyAI.apk
./gradlew assembleDebug                        # ya da Android Studio'dan Run ▶
```

Uygulamayı kurun → kullanın. Anahtar zaten gömülüdür; isterseniz
⚙ **Ayarlar → API Anahtarı** ile değiştirin.

---

## 🧪 Testler

```bash
./gradlew test            # JVM birim testleri (SSE ayrıştırma, hata çıkarımı)
```

---

## 📡 API Kullanımı

- **Base URL:** `https://integrate.api.nvidia.com/v1/`
- **Model:** `z-ai/glm-5.3` (`BuildConfig.MODEL` — `app/build.gradle.kts` içinden değiştirilebilir; CI'daki `api-check` işindeki `MODEL` değişkenini de güncelleyin)
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

---

## 📈 Versioning

- **Uygulama sürümü:** `app/build.gradle.kts` → `versionCode` / `versionName` (semantik sürüm: `MAJOR.MINOR.PATCH`)
- **Bağımlılık sürümleri:** `gradle/libs.versions.toml` (Gradle Version Catalog) — tek dosyadan yönetilir
- **GitHub Actions** ve **AGP/Gradle** sürümleri de aynı kataloğa bağlıdır

Yeni sürüm için: `versionName` + `versionCode` artırın → commit → CI otomatik APK üretir.

---

## 🔒 Güvenlik

- Uygulama içi anahtar **EncryptedSharedPreferences** (Android Keystore, AES-256) ile saklanır.
- OkHttp loglama `BASIC` seviyesinde: `Authorization` başlığı asla log'a yazılmaz (release'da log tamamen kapalı).
- Global crash handler: istisna loglanır, kullanıcı uyarılır, uygulama temiz görevle yeniden başlatılır (döngü korumalı).

---

## 📄 Lisans

Bu proje özel kullanım içindir.
