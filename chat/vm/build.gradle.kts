import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.yongda.ainativeagent.chat.vm"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":chat:ui"))          // 复用 UI 状态契约
            api(project(":llm:core"))         // 只依赖抽象 LlmProvider，不绑定具体实现
            api(project(":tool:core"))        // 通用 AgentTool / ToolRegistry：多步 Agent Loop 面向工具集合
            api(project(":tool:battery"))     // 电量工具抽象与契约（纯逻辑，无 Android）
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
