import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// 构建时从 local.properties 读 DEEPSEEK_API_KEY，编译进 BuildConfig 供真机 App 读取。
// key 不入库（local.properties 已被 .gitignore 忽略）。注意：仅供本地/调试，勿用于 release。
val deepSeekApiKey: String = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return@run ""
    val props = Properties()
    f.inputStream().use { props.load(it) }
    props.getProperty("DEEPSEEK_API_KEY", "")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.compose.uiTestManifest)

    androidTestImplementation(project(":chat:ui"))
    androidTestImplementation(libs.compose.uiTestJunit4)
    androidTestImplementation(libs.compose.runtime)
    androidTestImplementation(libs.compose.material3)
    androidTestImplementation(libs.compose.foundation)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.junit)
}

android {
    namespace = "com.yongda.ainativeagent"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yongda.ainativeagent"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekApiKey\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            // 让 debug 与 release 共存：debug 包名加 .debug 后缀，独立成另一个 app 槽。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 个人/学习项目：复用 AGP 自带的 debug 密钥签名，使 release 包可直接安装。
            // 注意：debug 密钥仅供本地，不能用于正式上架应用市场。
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}