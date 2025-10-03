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
        // Add Google Maven repository here for MediaPipe artifacts
        maven { url = uri("https://maven.google.com") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()        // Android and Google artifacts
        mavenCentral()  // Standard Maven Central
        // Keep only standard repositories for dependencies
    }
}

rootProject.name = "Circle2capture"
include(":app")
