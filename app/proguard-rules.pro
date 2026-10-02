# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Dokan Pro ----
# Keep line numbers for readable crash reports (file names are still obfuscated).
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Room entities and DAOs are accessed by generated code; keep their members stable for migrations.
-keep class com.example.data.entity.** { *; }

# Moshi/Retrofit are on the classpath (codegen via KSP); keep generated adapters if any are added.
-keep class **JsonAdapter { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
