pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "master-control"

include(":app")

// core ---------------------------------------------------------------
include(":core:common")
include(":core:logging")
include(":core:security")
include(":core:datastore")
include(":core:database")
include(":core:ui")

// domain / data ------------------------------------------------------
include(":domain")
include(":data")
include(":telegram")
include(":worker")

// features -----------------------------------------------------------
include(":feature:onboarding")
include(":feature:dashboard")
include(":feature:library")
include(":feature:video")
include(":feature:upload")
include(":feature:channels")
include(":feature:categories")
include(":feature:folders")
include(":feature:settings")
include(":feature:activity")
