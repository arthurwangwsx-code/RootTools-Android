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
    ?: "${System.getProperty("user.home")}/Library/Android/sdk"
val nativeBridgeClang = file(
    "$androidSdkRoot/ndk/28.2.13676358/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang",
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
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

