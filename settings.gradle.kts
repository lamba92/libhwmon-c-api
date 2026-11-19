pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "3.19.2"
}

val isCI = System.getenv("CI")?.equals("true", ignoreCase = true) == true
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        publishing.onlyIf { isCI }
        if (isCI) {
            tag("CI")
            System.getenv("GITHUB_WORKFLOW")?.let { tag(it) }
        }
    }
}

rootProject.name = "libhwmon-kotlin-api"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
