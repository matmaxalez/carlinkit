pluginManagement {
    repositories {
        google()
        // Lustro Maven Central (Google) — omija limity 429 repo.maven.apache.org.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Lustro Maven Central (Google) — omija limity 429 repo.maven.apache.org.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "LekkiLauncher"
include(":app")
