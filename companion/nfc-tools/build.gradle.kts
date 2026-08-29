plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.arthur.nfclab"
    compileSdk = 37
    compileSdkMinor = 1
    buildToolsVersion = "37.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.arthur.nfclab"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val nativeBridgeAssetsDir = layout.buildDirectory.dir("generated/nativeBridgeAssets")
val nativeBridgeSource = file("src/main/cpp/nfc_root_bridge.c")
val androidSdkRoot = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "${System.getProperty("user.home")}/Library/Android/sdk"
val ndkHostTag = when {
    System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> "linux-x86_64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "darwin-x86_64"
    else -> error("Unsupported NDK host: ${System.getProperty("os.name")}")
}
val nativeBridgeClang = file(
    "$androidSdkRoot/ndk/28.2.13676358/toolchains/llvm/prebuilt/$ndkHostTag/bin/aarch64-linux-android26-clang",
)
val nativeBridgeOutput = nativeBridgeAssetsDir.map {
    it.file("rootbridge/arm64-v8a/nfc-root-bridge").asFile
}

val buildNativeRootBridge by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the arm64 root NFC diagnostic helper packaged with NFC Lab."
    inputs.file(nativeBridgeSource)
    outputs.file(nativeBridgeOutput)

    doFirst {
        require(nativeBridgeClang.isFile) {
            "Android NDK clang not found: ${nativeBridgeClang.absolutePath}"
        }
        nativeBridgeOutput.get().parentFile.mkdirs()
        commandLine(
            nativeBridgeClang.absolutePath,
            "-std=c17",
            "-O2",
            "-fPIE",
            "-pie",
            "-Wall",
            "-Wextra",
            nativeBridgeSource.absolutePath,
            "-o",
            nativeBridgeOutput.get().absolutePath,
        )
    }
}

android.sourceSets.getByName("main").assets.directories.add(nativeBridgeAssetsDir.get().asFile.absolutePath)

tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(buildNativeRootBridge)
}

tasks.matching { it.name.lowercase().contains("lint") }.configureEach {
    dependsOn(buildNativeRootBridge)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.nfc)
    implementation(libs.androidx.activity.compose.companion)
    implementation(libs.androidx.lifecycle.runtime.ktx.companion)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
}
