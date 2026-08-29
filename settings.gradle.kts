pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info/")
    }
}

rootProject.name = "RootTools"
include(":app")
include(":companion:hyperos-credential-fix")
include(":companion:background-server")
include(":companion:nfc-tools")
include(":feature:network-inspection")
