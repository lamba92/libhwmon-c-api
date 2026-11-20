@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
}

group = "io.github.lamba92.libhwmon"
version =
    System.getenv("RELEASE_VERSION")
        ?: System.getenv("GITHUB_REF_NAME")?.removePrefix("v")
        ?: "1.0.0-SNAPSHOT"

val nativeBinariesDir = layout.buildDirectory.dir("binaries/win-x64")
val libFile = nativeBinariesDir.map { it.file("libhwmon.lib") }

val publishNativeHwmonLib by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles LibHwmon NativeAOT static library using dotnet"
    workingDir = projectDir
    inputs.dir("src/mingwMain/csharp")
    inputs.file("LibHwmon.csproj")

    val dotnetPath =
        file("${System.getProperty("user.home")}/.dotnet/dotnet.exe").takeIf { it.exists() }?.absolutePath
            ?: file("${System.getProperty("user.home")}/.dotnet/dotnet").takeIf { it.exists() }?.absolutePath
            ?: "dotnet"

    commandLine(
        dotnetPath,
        "publish",
        "LibHwmon.csproj",
        "-c",
        "Release",
        "-r",
        "win-x64",
        "-o",
        nativeBinariesDir.get().asFile.absolutePath,
    )

    doLast {
        val intermediateLib = file("bin/Release/net8.0-windows/win-x64/native/libhwmon.lib")
        if (intermediateLib.exists()) {
            intermediateLib.copyTo(libFile.get().asFile, overwrite = true)
        }
    }

    outputs.file(libFile)
}

kotlin {
    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                create("libhwmon") {
                    defFile(file("src/nativeInterop/cinterop/libhwmon.def"))
                    includeDirs(file("src/nativeInterop/cinterop"))
                }
            }
            compileTaskProvider.configure {
                dependsOn(publishNativeHwmonLib)
            }
            tasks.named("cinteropLibhwmonMingwX64").configure {
                dependsOn(publishNativeHwmonLib)
            }
        }

        binaries {
            all {
                linkerOpts(
                    "-L${nativeBinariesDir.get().asFile.absolutePath}",
                    "-llibhwmon",
                )
            }
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
            }
        }
        mingwMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        mingwTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    val nativeLinkageAttribute = Attribute.of("io.github.lamba92.native.linkage", String::class.java)

    val mingwX64NativeBinariesElements by configurations.consumable("mingwX64NativeBinariesElements") {
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "native-binary"))
            attribute(
                OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                objects.named(
                    OperatingSystemFamily::class.java,
                    OperatingSystemFamily.WINDOWS,
                ),
            )
            attribute(
                MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
                objects.named(
                    MachineArchitecture::class.java,
                    MachineArchitecture.X86_64,
                ),
            )
            attribute(nativeLinkageAttribute, "shared")
        }
    }

    artifacts {
        add(mingwX64NativeBinariesElements.name, nativeBinariesDir.map { it.file("libhwmon.dll") }) {
            builtBy(publishNativeHwmonLib)
        }
        add(mingwX64NativeBinariesElements.name, libFile) {
            builtBy(publishNativeHwmonLib)
        }
    }

    publishing {
        adhocSoftwareComponent {
            addVariantsFromConfiguration(mingwX64NativeBinariesElements) {
                mapToMavenScope("runtime")
            }
        }
    }
}

val testRepoDir = layout.buildDirectory.dir("m2")

publishing {
    repositories {
        maven {
            name = "TestLocal"
            url = uri(testRepoDir)
        }
    }
}

tasks.register("publishToTestLocal") {
    group = "publishing"
    description = "Publishes all publications to build/m2 for inspection"
    dependsOn("publishAllPublicationsToTestLocalRepository")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "libhwmon-kotlin-api", version.toString())

    pom {
        name = "libhwmon-kotlin-api"
        description = "Kotlin/Native and C ABI bindings for LibreHardwareMonitor using .NET NativeAOT"
        inceptionYear = "2026"
        url = "https://github.com/lamba92/libhwmon-kotlin-api"
        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "lamba92"
                name = "Lamberto Basti"
                url = "https://github.com/lamba92"
            }
        }
        scm {
            url = "https://github.com/lamba92/libhwmon-kotlin-api"
            connection = "scm:git:git://github.com/lamba92/libhwmon-kotlin-api.git"
            developerConnection = "scm:git:ssh://git@github.com/lamba92/libhwmon-kotlin-api.git"
        }
    }
}

ktlint {
    version = libs.versions.ktlint.asProvider()
}
