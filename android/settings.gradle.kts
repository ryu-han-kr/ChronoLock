pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ChronoLock"
include(":domain")
// Allows domain tests on machines that have JDK 17 but no Android SDK.
if (!providers.gradleProperty("domainOnly").isPresent) include(":app")
