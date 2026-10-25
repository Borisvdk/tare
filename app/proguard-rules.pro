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

# Anthropic Java SDK: model classes are (de)serialized by Jackson via reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
