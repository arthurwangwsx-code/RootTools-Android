buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9.x 使用内置 Kotlin；这里显式提升 KGP，供 Compose Compiler plugin 使用。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

