# Keep names readable for crash reports; we only want tree-shaking (mainly icons).
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep class com.hasantasdelen.guider.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# PDFBox (text extraction for read-aloud)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**

# jsoup / okhttp / mlkit
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn org.jspecify.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.gms.internal.mlkit_language_id_common.** { *; }
-dontwarn javax.annotation.**
-ignorewarnings
