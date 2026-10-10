import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// release 签名。密钥库和口令都在仓外（../signing/，桌面另有一份备份），
// 仓里只有 keystore.properties，它已经在 .gitignore 里，永远不许提交。
// 这个文件不在的时候 release 构建出来的是 app-release-unsigned.apk —— 别当正式包发。
// 换过 key 之后包就不能覆盖 debug 签名的安装了，装了旧包的得先卸载。
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    // 必须用 Reader 版 load：Properties 老的 InputStream 版按 ISO-8859-1 解码，
    // 而桌面那个路径带中文（Desktop/安大信相关文件/andaxin-signing），会读成乱码找不到文件。
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

android {
    namespace = "com.linxin"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        applicationId = "com.linxin"
        versionCode = 65
        versionName = "1.3.7-beta41"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 换签名之后 debug 包和 release 包不能互相覆盖，为了"不动你正在用的那个包"
        // 留了这个口子：-PappIdSuffix=.rtest 会编成另一个应用 id，可以并排装机做冒烟测试。
        val appIdSuffix = project.providers.gradleProperty("appIdSuffix").orNull
        if (!appIdSuffix.isNullOrBlank()) applicationIdSuffix = appIdSuffix
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                // PKCS12 不区分键口令，keyPassword 就是库口令
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                abiFilters += "arm64-v8a"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image
    implementation(libs.coil.compose)

    // CameraX + ML Kit (AI课堂扫码)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // ZXing core (锻炼考勤二维码生成；扫描仍用 ML Kit)
    implementation(libs.zxing.core)

    // Haze (Apple 风格毛玻璃底栏背景模糊)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Miuix (小米 HyperOS 风格 UI 库)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)

    // Kyant Liquid Glass (真·液态玻璃：refraction lens + vibrancy + highlight)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    testImplementation("junit:junit:4.13.2")
}
