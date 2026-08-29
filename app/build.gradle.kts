plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlinx.kover")
}

val releaseKeystorePath = System.getenv("ROOTTOOLS_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ROOTTOOLS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ROOTTOOLS_KEY_ALIAS")
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.arthur.roottools"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arthur.roottools"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-beta.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    lint {
        // AGP/Lint currently crashes inside UnsafeIntentLaunchDetector while resolving K2
        // lazy properties in TileService. Keep every other lint check enabled rather than
        // disabling lint for the project.
        disable += "UnsafeIntentLaunch"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("rootToolsRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeystorePassword)
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("rootToolsRelease")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/libpcapd.so"
    }
}

dependencies {
    implementation(project(":core:privilege"))
    implementation(project(":feature:network-inspection"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.bouncycastle.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
}
