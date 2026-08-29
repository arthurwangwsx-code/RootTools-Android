plugins {
    id("com.android.application")
}

android {
    namespace = "com.arthur.hyperos.credentialfix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arthur.hyperos.credentialfix"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
