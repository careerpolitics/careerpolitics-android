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
    }
}

rootProject.name = "CareerPolitics"
include(":app")
include(":core:common")
include(":feature:shell")
include(":feature:deeplink")
include(":feature:auth")
include(":feature:notifications")
include(":data:auth")
