import com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

group = "io.github.lamba92.libhwmon"
version = "0.1.0-SNAPSHOT"

val nativeBinariesDir = layout.buildDirectory.dir("binaries/win-x64")
val dllFile = nativeBinariesDir.map { it.file("libhwmon.dll") }
val libFile = nativeBinariesDir.map { it.file("libhwmon.lib") }

val publishNativeHwmonDll by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles LibHwmon NativeAOT dynamic library using dotnet"
    workingDir = projectDir
    inputs.dir("src/mingwMain/csharp")
    inputs.file("LibHwmon.csproj")

    val dotnetPath =
        file("${System.getProperty("user.home")}/.dotnet/dotnet").takeIf { it.exists() }?.absolutePath
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

    outputs.file(dllFile)
    outputs.file(libFile)
}

kotlin {
    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                val libhwmon by creating {
                    defFile(file("src/nativeInterop/cinterop/libhwmon.def"))
                    includeDirs(file("src/nativeInterop/cinterop"))
                }
            }
            compileTaskProvider.configure {
                dependsOn(publishNativeHwmonDll)
            }
            tasks.named("cinteropLibhwmonMingwX64").configure {
                dependsOn(publishNativeHwmonDll)
            }
        }

        binaries {
            all {
                linkerOpts("-L${nativeBinariesDir.get().asFile.absolutePath}", "-llibhwmon")
            }
        }
    }

    sourceSets {
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
    publishToMavenCentral(CENTRAL_PORTAL)
    signAllPublications()

    coordinates(group.toString(), "libhwmon-kotlin-api", version.toString())

    pom {
        name = "libhwmon-kotlin-api"
        description = "Kotlin/Native and C ABI bindings for LibreHardwareMonitor using .NET NativeAOT"
        inceptionYear = "2024"
        url = "https://github.com/lamba92/libhwmon-kotlin-api"
        licenses {
            license {
                name = "Mozilla Public License 2.0"
                url = "https://www.mozilla.org/en-US/MPL/2.0/"
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
