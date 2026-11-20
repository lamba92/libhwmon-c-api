# libhwmon-kotlin-api

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lamba92.libhwmon/libhwmon-kotlin-api)](https://central.sonatype.com/artifact/io.github.lamba92.libhwmon/libhwmon-kotlin-api)
[![GitHub Release](https://img.shields.io/github/v/release/lamba92/libhwmon-c-api)](https://github.com/lamba92/libhwmon-c-api/releases)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Kotlin/Native (`mingwX64`) client and C ABI for [LibreHardwareMonitorLib](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) powered by **.NET NativeAOT**.

Provides a strongly-typed, coroutine-first Kotlin API for reading PC hardware sensors (temperatures, fan speeds, loads, voltages, powers) and controlling fan/pump PWM duty cycles.

---

## Features

- **No Tree-Shaking Losses**: `LibHwmon.csproj` roots `LibreHardwareMonitorLib` (`<TrimmerRootAssembly Include="LibreHardwareMonitorLib" />`), preventing NativeAOT from stripping internal hardware probes and drivers.
- **Pure Kotlin Surface**: Pointer handles and unmanaged structs are completely internal. The public API exposes only immutable `@Serializable` data classes and type-safe value classes (`HardwareId`, `SensorId`).
- **High-Performance Batch Telemetry**: Telemetry polling reads all sensor metrics across the entire hardware tree in a single C-call directly from C# memory.
- **Coroutines & Flows**: Collect live telemetry smoothly via `client.telemetryFlow(1.seconds)`.
- **Thread Safety**: All ring-0 unmanaged calls are guarded by a process-global mutex and pinned to a dedicated single-threaded dispatcher context.
- **Safe Error Handling**: C exceptions are caught before the ABI boundary and mapped to structured `HwmonStatus` codes, surfacing in Kotlin as strongly-typed exceptions (`HwmonUnauthorizedException`, `HwmonNotFoundException`, etc.).
- **Privilege Enforcement**: Validates Administrator elevation on initialization before loading kernel drivers.
- **Variant-Aware Publishing**: Publishes both Kotlin metadata/KLIBs and native binaries (`libhwmon.dll` and `libhwmon.lib`) with standard Gradle attributes.

---

## Adding the Dependency

In your consumer `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "com.example.main"
            }
        }
    }

    sourceSets {
        mingwMain.dependencies {
            implementation("io.github.lamba92.libhwmon:libhwmon-kotlin-api:1.0.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
    }
}
```

---

## Consuming Native Binaries (`libhwmon.dll` & `libhwmon.lib`)

Because `libhwmon` is a NativeAOT shared library, consumer executables need `libhwmon.dll` at runtime (either placed in the same directory as the executable or on the system `PATH`).

The publication includes a Gradle variant with native attributes:
- `org.gradle.category` = `library`
- `org.gradle.usage` = `native-binary`
- `org.gradle.native.operatingSystem` = `windows`
- `org.gradle.native.architecture` = `x86-64`
- `io.github.lamba92.native.linkage` = `shared`

### 1. Declare a Resolvable Configuration with Attributes

In your `build.gradle.kts`, create a configuration that requests these attributes:

```kotlin
val nativeLinkageAttribute = Attribute.of("io.github.lamba92.native.linkage", String::class.java)

val hwmonNativeBinaries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-binary"))
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(OperatingSystemFamily.WINDOWS))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture.X86_64))
        attribute(nativeLinkageAttribute, "shared")
    }
}

dependencies {
    hwmonNativeBinaries("io.github.lamba92.libhwmon:libhwmon-kotlin-api:1.0.0")
}
```

### 2. Copy the DLL Next to the Executable

You can automatically copy `libhwmon.dll` into the output directory of your executable:

```kotlin
// Option A: Standalone Copy task
val copyHwmonDll by tasks.registering(Copy::class) {
    description = "Copies libhwmon.dll to the executable output directory"
    from(hwmonNativeBinaries)
    into(layout.buildDirectory.dir("bin/mingwX64/debugExecutable"))
    include("*.dll")
}

// Option B: Automatically bundle the DLL after linking
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    doLast {
        val exeDir = destinationDirectory.get().asFile
        copy {
            from(hwmonNativeBinaries)
            into(exeDir)
            include("*.dll")
        }
    }
}
```

If you link directly against `libhwmon.lib`, you can also configure the linker:

```kotlin
kotlin {
    mingwX64 {
        binaries {
            executable {
                // Point linker to resolved native directory
                val nativeDir = hwmonNativeBinaries.elements.map { it.first().asFile.parentFile }
                linkerOpts(
                    "-L${nativeDir.get().absolutePath}",
                    "-llibhwmon",
                )
            }
        }
    }
}
```

---

## Example Usage

> [!IMPORTANT]
> LibreHardwareMonitor requires **Administrator elevation** to load kernel ring-0 drivers (such as WinRing0 / PawnIO) and access hardware sensors. Running without Administrator rights throws `HwmonUnauthorizedException`.

```kotlin
package com.example

import io.github.lamba92.libhwmon.HwmonClient
import io.github.lamba92.libhwmon.HwmonConfig
import io.github.lamba92.libhwmon.HwmonUnauthorizedException
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() = runBlocking {
    // 1. Configure hardware subsystems to monitor
    val config = HwmonConfig(
        enableCpu = true,
        enableGpu = true,
        enableMotherboard = true,
        enableMemory = true,
        enableStorage = true,
        enableNetwork = true,
        enableController = true,
        enableBattery = true,
    )

    // 2. Initialize client with privilege check
    val client = try {
        HwmonClient(config)
    } catch (e: HwmonUnauthorizedException) {
        println("ERROR: Administrator privileges required to load hardware drivers.")
        return@runBlocking
    }

    client.use { hwmon ->
        // 3. Inspect hardware and sensor tree topology
        println("--- Hardware Tree ---")
        val tree = hwmon.getHardwareTree()
        tree.forEach { hw ->
            println("${hw.name} [${hw.type}] (id: ${hw.id})")
            hw.sensors.forEach { sensor ->
                println("  - [${sensor.type}] ${sensor.name} (id: ${sensor.id})")
            }
        }

        // 4. One-time telemetry snapshot
        println("\n--- Telemetry Snapshot ---")
        val snapshot = hwmon.sampleTelemetry()
        snapshot.hardware.forEach { hw ->
            hw.sensors.forEach { s ->
                val unit = when (s.type.name) {
                    "TEMPERATURE" -> "°C"
                    "FAN" -> "RPM"
                    "VOLTAGE" -> "V"
                    "POWER" -> "W"
                    "LOAD" -> "%"
                    else -> ""
                }
                println("${s.name}: ${s.value} $unit")
            }
        }

        // 5. Stream real-time telemetry updates as a Coroutine Flow
        println("\n--- Streaming Live Telemetry (Press Ctrl+C to stop) ---")
        hwmon.telemetryFlow(1.seconds).collect { liveSnapshot ->
            val cpu = liveSnapshot.hardware.firstOrNull { it.name.contains("CPU", ignoreCase = true) }
            val cpuPackageTemp = cpu?.sensors?.firstOrNull { 
                it.name.contains("Package", ignoreCase = true) 
            }
            println("Live CPU Temp: ${cpuPackageTemp?.value ?: "N/A"} °C")
        }

        // 6. Fan / Pump Control (optional)
        // hwmon.setControlSoftware(fanSensorId, 75f) // Set fan to 75%
        // hwmon.restoreControlDefault(fanSensorId)   // Hand control back to BIOS/firmware
    }
}
```

---

## Diagnostic Sample App

A ready-to-run diagnostic CLI tool is included in the `:sample` module:

```bash
# Run the sample diagnostic inspector (from an elevated terminal)
./gradlew :sample:run
```

---

## Building from Source

Building requires a Windows x64 environment with:
- **.NET 8.0 SDK** (with NativeAOT support & MSVC tools)
- **JDK 21+**
- Visual Studio C++ Build Tools (or MSVC toolchain)

```bash
# Build KMP library, NativeAOT binaries, and run tests
./gradlew build
```

---

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).
LibreHardwareMonitorLib is licensed under the Mozilla Public License 2.0 (MPL 2.0).
