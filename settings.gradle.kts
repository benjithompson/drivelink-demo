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

rootProject.name = "drivelink-demo"

include(":app")
include(":core:designsystem")
include(":core:domain")
include(":core:network")
include(":core:settings")
include(":core:data")
include(":core:testing")
