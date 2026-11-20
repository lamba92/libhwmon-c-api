plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
}

group = "io.github.lamba92.libhwmon"
version = "0.1.0-SNAPSHOT"

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
