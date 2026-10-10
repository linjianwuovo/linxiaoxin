# ProGuard rules for LightXin

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.lightxin.**.data.** { *; }
-keep class com.lightxin.**.domain.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# RSA keys
-keep class com.lightxin.core.auth.RSAUtils { *; }

# CameraX — release 包扫码必须保留，否则 ImageProxy/PreviewView 被优化后识别失效
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# ML Kit Barcode Scanning — 必须 keep 整个 com.google.mlkit 树，因为内部依赖注入
# 在 MlKitInitProvider 启动时解析组件，混淆后接口名/类名不匹配会导致直接崩溃
-keep class com.google.mlkit.** { *; }

# ── 本 fork 的实际包名是 com.linxin，上面那批 com.lightxin 规则是上游留下的，
#    一条都命中不了：Gson 靠反射按字段名反序列化，模型被混淆/删掉就是 release 包
#    接口解析全废。历史上每个正式版都是 debug 包（没混淆），所以一直没暴露。
#    两条都留着：com.lightxin 只存在于测试源码里，不打包，留着无害。
-keep class com.linxin.**.data.** { *; }
-keep class com.linxin.**.domain.** { *; }
-keep class com.linxin.core.settings.** { *; }
-keep class com.linxin.core.network.**Response* { *; }
