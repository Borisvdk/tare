# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# Ktor / OkHttp optional platform classes.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Anthropic Java SDK ships its own consumer rules (META-INF/proguard/anthropic-java-core.pro)
# for Jackson; only silence references to classes that don't exist on Android.
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
# Only used by the SDK's class-based structured outputs (we pass a JSON schema instead).
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn com.github.victools.jsonschema.**

# ML Kit starts its components (barcode model, executors, shared prefs) from registrars named
# in the manifest and created by reflection. R8 full mode drops constructors it never sees
# called, so without this ML Kit comes up empty and both scanners crash in release builds only.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
