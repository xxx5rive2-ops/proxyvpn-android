# NetGuard Pro — Release ProGuard Rules
# Production-grade rules for complete obfuscation while preserving functionality

##──────────────────────────────────────────────────────────────
## ANDROID CORE
##──────────────────────────────────────────────────────────────
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.service.quicksettings.TileService
-keepclassmembers class * extends android.content.Context {
    public void *(android.view.View);
    public void *(android.view.MenuItem);
}

##──────────────────────────────────────────────────────────────
## KOTLIN
##──────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-dontnote kotlin.**

##──────────────────────────────────────────────────────────────
## HILT / DAGGER
##──────────────────────────────────────────────────────────────
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <fields>;
}

##──────────────────────────────────────────────────────────────
## ROOM DATABASE
##──────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }

##──────────────────────────────────────────────────────────────
## KOTLINX SERIALIZATION
##──────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.netguard.**$$serializer { *; }
-keepclassmembers class app.netguard.** {
    *** Companion;
}
-keepclasseswithmembers class app.netguard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

##──────────────────────────────────────────────────────────────
## OKHTTP / RETROFIT
##──────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }

##──────────────────────────────────────────────────────────────
## NETGUARD DOMAIN ENTITIES (must survive obfuscation for DB)
##──────────────────────────────────────────────────────────────
-keep class app.netguard.domain.entity.** { *; }
-keep class app.netguard.domain.model.** { *; }

##──────────────────────────────────────────────────────────────
## VPN ENGINE — critical path, no obfuscation risk
##──────────────────────────────────────────────────────────────
-keep class app.netguard.engine.vpn.** { *; }
-keep class app.netguard.engine.proxy.** { *; }

##──────────────────────────────────────────────────────────────
## SECURITY — never obfuscate crypto classes
##──────────────────────────────────────────────────────────────
-keep class app.netguard.core.security.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

##──────────────────────────────────────────────────────────────
## REFLECTION SAFETY
##──────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Exceptions

##──────────────────────────────────────────────────────────────
## REMOVE LOGGING IN RELEASE
##──────────────────────────────────────────────────────────────
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
