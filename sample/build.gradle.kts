plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ktlint)
}

val nativeBinariesDir = rootProject.layout.buildDirectory.dir("binaries/win-x64")

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "io.github.lamba92.libhwmon.sample.main"
                linkerOpts(
                    "-L${nativeBinariesDir.get().asFile.absolutePath}",
                    "-llibhwmon",
                )
            }
        }
    }

    sourceSets {
        mingwMain.dependencies {
            implementation(project(":"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    dependsOn(rootProject.tasks.named("publishNativeHwmonLib"))
    doLast {
        val exeDir = destinationDirectory.get().asFile
        val dll = nativeBinariesDir.get().asFile.resolve("libhwmon.dll")
        if (dll.exists()) {
            dll.copyTo(exeDir.resolve("libhwmon.dll"), overwrite = true)
        }
    }
}

tasks.named<Exec>("runDebugExecutableMingwX64").configure {
    val binDir = nativeBinariesDir.get().asFile.absolutePath
    val currentPath = System.getenv("Path") ?: ""
    environment("Path", "$binDir;$currentPath")
}

tasks.register("run") {
    group = "application"
    description = "Runs the sample executable"
    dependsOn("runDebugExecutableMingwX64")
}

ktlint {
    version = libs.versions.ktlint.asProvider()
}
