# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Go / libXray & hev-socks5-tunnel JNI bindings
-keep class libXray.** { *; }
-keep class go.** { *; }
-keep class org.amnezia.awg.hevtunnel.** { *; }
-dontwarn java.lang.invoke.StringConcatFactory

# kotlinx.serialization (nav routes, SubscriptionExternalLink, etc.)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class com.example.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.example.data.model.** { *; }
-keep,includedescriptorclasses class *$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    public kotlinx.serialization.KSerializer serializer(...);
}

# Room database
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep stack trace line numbers cleanly
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
