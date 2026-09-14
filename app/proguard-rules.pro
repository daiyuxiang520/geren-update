# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/user/Library/Android/sdk/tools/proguard/proguard-android.txt

# ===== 通用优化 =====
# 启用代码压缩
-optimizationpasses 5
# 混淆时不使用大小写混合，混淆后的类名小写
-dontusemixedcaseclassnames
# 不跳过非公共的库的类成员
-dontskipnonpubliclibraryclassmembers
# 混淆时不生成大小写混合的类名
-dontusemixedcaseclassnames
# 不预校验
-dontpreverify
# 忽略警告
-ignorewarnings
# 优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification
# 确定统一的混淆类的成员名称来增加混淆
-useuniqueclassmembernames
# 优化指定的代码
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ===== 移除日志 =====
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ===== Kotlin =====
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowobfuscation class * {
    @kotlin.Metadata *;
}
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ===== Gson =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Data Model =====
-keep class com.open.wuling.data.model.** { *; }
-keep class com.open.wuling.data.api.** { *; }

# Keep data classes for Gson serialization
-keepclassmembers class com.open.wuling.data.model.** {
    <init>(...);
}
-keepclassmembers class com.open.wuling.data.api.** {
    <init>(...);
}

# ===== Compose =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== Hilt =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== 应用特定 =====
-keep class com.open.wuling.** { *; }
-keepclassmembers class com.open.wuling.** {
    public *;
}

# 保留应用入口点
-keep class com.open.wuling.MainActivity { *; }
-keep class com.open.wuling.WulingApplication { *; }

# ===== Native libraries =====
-keepclasseswithmembernames class * {
    native <methods>;
}
