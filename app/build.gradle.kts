import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// API ANAHTARI YÖNETİMİ
//
// Anahtar ASLA kaynak koda yazılmaz. Derleme zamanında aşağıdaki
// önceliğe göre bulunur ve BuildConfig'e gömülür:
//   1. -Pnvidia_api_key=...        (komut satırı / GitHub Actions)
//   2. NVIDIA_API_KEY              (ortam değişkeni / GitHub Secrets)
//   3. local.properties            (yerel geliştirme)
// Uygulama, çalıştırma zamanında Ayarlar ekranından girilen anahtarı da
// destekler (cihazda şifreli saklanır ve gömülü anahtarı ezer).
// ---------------------------------------------------------------------------
val localProperties = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    file.inputStream().use { localProperties.load(it) }
}

val nvidiaApiKey: String = (project.findProperty("nvidia_api_key")
    ?: rootProject.findProperty("nvidia_api_key")
    ?: System.getenv("NVIDIA_API_KEY")
    ?: localProperties.getProperty("nvidia_api_key")
    ?: "")
    .toString()
    .trim()
    .replace("\\", "")
    .replace("\"", "")

// Sistem promptu (Türkçe asistan kişiliği)
val systemPrompt: String =
    "Sen Raiwesy AI'sın; kullanıcısına nazik, net ve faydalı yanıtlar veren bir asistansın. " +
        "Varsayılan olarak Türkçe yaz. Kullanıcı başka bir dilde sorarsa o dilde yanıtla. " +
        "Kod, liste ve adım adım açıklama gibi uygun durumlarda formatlı yanıt ver."

android {
    namespace = "com.raiwesy.ai"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.raiwesy.ai"
        minSdk = 26
        targetSdk = 35

        // Versioning: semantik sürüm (MAJOR.MINOR.PATCH)
        versionCode = 2
        versionName = "1.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig sabitleri - anahtar yalnızca derleme zamanında buraya gelir
        buildConfigField("String", "NVIDIA_API_KEY", "\"$nvidiaApiKey\"")
        buildConfigField("String", "API_BASE_URL", "\"https://integrate.api.nvidia.com/v1/\"")
        buildConfigField("String", "MODEL", "\"z-ai/glm-5.3\"")
        buildConfigField("String", "SYSTEM_PROMPT", "\"$systemPrompt\"")
    }

    // Opsiyonel release imzalama (local.properties üzerinden)
    val releaseKeystoreFile = localProperties.getProperty("releaseStoreFile")
        ?.let { rootProject.file(it) }
        ?: rootProject.file("release.keystore").takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystoreFile != null &&
            !localProperties.getProperty("releaseStorePassword").isNullOrBlank()
        ) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = localProperties.getProperty("releaseStorePassword")
                keyAlias = localProperties.getProperty("releaseKeyAlias")
                keyPassword = localProperties.getProperty("releaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // CI hızı ve güvenilirliği için release lint'i kapatıldı
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    // AndroidX çekirdek
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.security.crypto)

    // MVVM / Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose - Material Design 3
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Ağ katmanı: Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Testler
    testImplementation(libs.junit)
}
