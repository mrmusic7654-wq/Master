# Master Control release shrinking rules.
#
# R8 full mode is used. Only rules that cannot be derived from the code itself
# are listed here; every rule has a reason.

# TDLib is reached through one JNI bridge class. The native side resolves these
# methods by name, so they must keep their signatures.
-keepclasseswithmembernames class com.mastercontrol.app.telegram.TdJsonJni {
    native <methods>;
    <methods>;
}

# kotlinx.serialization generates serializers that are looked up reflectively
# when a @Serializable type is serialized through a Json instance.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.mastercontrol.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.mastercontrol.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generates implementations at compile time; no reflection rules needed.
# Hilt/Dagger generate code at compile time; no reflection rules needed.

# Keep line numbers for readable crash reports without exposing sources.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
