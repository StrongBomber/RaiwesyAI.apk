# RaiwesyAI ProGuard kuralları (Release - R8)

# Gson ile serileştirilen API modelleri adları korunmalı
-keep class com.raiwesy.ai.data.model.** { *; }
-keepclassmembers class com.raiwesy.ai.data.** {
    <fields>;
}

# Retrofit/OkHttp - tip güvenliği için genel kural
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson: SerializedName alanlarını koru
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Uygulama giriş noktaları
-keep class com.raiwesy.ai.RaiwesyApp { *; }
-keep class com.raiwesy.ai.MainActivity { *; }

# Log kütüphanesi uyarılarını bastır
-dontwarn java.lang.invoke.StringConcatFactory
