import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

val releaseKeystorePath = System.getenv("ROOTTOOLS_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ROOTTOOLS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ROOTTOOLS_KEY_ALIAS")
val rootToolsVersionCode = providers.gradleProperty("rootToolsVersionCode").get().toInt()
val rootToolsVersionName = providers.gradleProperty("rootToolsVersionName").get()
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
).all { !it.isNullOrBlank() }

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            defaultConfig {
                versionCode = rootToolsVersionCode
                versionName = rootToolsVersionName
            }
            if (releaseSigningConfigured) {
                val rootToolsRelease = signingConfigs.create("rootToolsRelease") {
                    storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                    storePassword = requireNotNull(releaseKeystorePassword)
                    keyAlias = requireNotNull(releaseKeyAlias)
                    keyPassword = requireNotNull(releaseKeystorePassword)
                }
                buildTypes.named("release") {
                    signingConfig = rootToolsRelease
                }
            }
        }
    }
}
