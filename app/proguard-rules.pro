# Основные правила приложения
-keep class ru.alemak.studentapp.** { *; }

# AndroidX
-keep class androidx.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Библиотеки
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class coil.** { *; }

# Apache POI
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# Kotlin
-keep class kotlin.** { *; }

# Универсальные правила для распространенных проблем
-keep class * implements org.xml.sax.EntityResolver
-keep class * implements java.io.Serializable

# Игнорировать ВСЕ предупреждения о missing classes
-dontwarn **

# Сохранять аннотации и метаданные
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Для сериализации
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Для Retrofit
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}