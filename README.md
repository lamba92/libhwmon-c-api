# libhwmon-kotlin-api

Kotlin/Native (`mingwX64`) client and C ABI for [LibreHardwareMonitorLib](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) powered by **.NET NativeAOT**.

Provides a strongly-typed, coroutine-first Kotlin API for reading PC hardware sensors (temperatures, fan speeds, loads, voltages, powers) and controlling fan/pump PWM duty cycles.

## Features

- **No Tree-Shaking Losses**: `LibHwmon.csproj` roots `LibreHardwareMonitorLib` (`<TrimmerRootAssembly Include="LibreHardwareMonitorLib" />`), preventing NativeAOT from stripping internal hardware probes and drivers.
- **Pure Kotlin Surface**: Pointer handles and unmanaged structs are completely internal. The public API exposes only immutable `@Serializable` data classes and type-safe value classes (`HardwareId`, `SensorId`).
- **High-Performance Batch Telemetry**: Telemetry polling reads all sensor metrics across the entire hardware tree in a single C-call directly from C# memory.
- **Coroutines & Flows**: Collect live telemetry smoothly via `client.telemetryFlow(1.seconds)`.
- **Thread Safety**: All ring-0 unmanaged calls are guarded by a process-global mutex and pinned to a dedicated single-threaded dispatcher context.
- **Safe Error Handling**: C exceptions are caught before the ABI boundary and mapped to structured `HwmonStatus` codes, surfacing in Kotlin as strongly-typed exceptions (`HwmonUnauthorizedException`, `HwmonNotFoundException`, etc.).
- **Privilege Enforcement**: Validates Administrator elevation on initialization before loading kernel drivers.

## Architecture

- **`LibHwmon.csproj`**: Compiles C# + LibreHardwareMonitor to a native static library (`libhwmon.lib`) via .NET NativeAOT.
- **`src/mingwMain/csharp/Lamba92/LibHwmon/`**: C# exports tagged with `[UnmanagedCallersOnly]`.
- **`src/nativeInterop/cinterop/`**: C ABI header (`libhwmon.h`) and `cinterop` definition (`libhwmon.def`).
- **`src/mingwMain/kotlin/io/github/lamba92/libhwmon/`**:
  - `Models.kt`: Immutable serializable models, value classes, and exception hierarchy.
  - `HwmonClient.kt`: Public `HwmonClient` interface and factory.
  - `WindowsHwmonClient.kt`: Concrete client with Mutex, dispatcher, and batch sampling.
  - `NativeHandles.kt`: Internal unmanaged pointer wrappers.

## Usage

```kotlin
import io.github.lamba92.libhwmon.HwmonClient
import io.github.lamba92.libhwmon.HwmonConfig
import kotlin.time.Duration.Companion.seconds

// Create client (throws HwmonUnauthorizedException if not run as Admin)
val client = HwmonClient()

// 1. Inspect hardware and sensor topology
val tree = client.getHardwareTree()
tree.forEach { hw ->
    println("${hw.name} (${hw.type})")
    hw.sensors.forEach { sensor ->
        println("  - ${sensor.name} [${sensor.type}]: ${sensor.id}")
    }
}

// 2. Stream telemetry as a Flow
client.telemetryFlow(1.seconds).collect { snapshot ->
    snapshot.hardware.forEach { hw ->
        hw.sensors.forEach { s ->
            println("${s.name}: ${s.value}")
        }
    }
}

// 3. Control fans / pumps
client.setControlSoftware(fanSensorId, 75f)

// 4. Restore BIOS defaults on exit
client.restoreAllControlDefaults()
client.close()
```

## Building

Building requires a Windows environment with the .NET 8+ SDK and MSVC C++ build tools (or via GitHub Actions):

```bash
./gradlew build
```

## Publishing to Maven Central

Publishing targets the modern [Sonatype Central Portal](https://central.sonatype.com/) via `com.vanniktech.maven.publish`.

### GitHub Actions Secrets
Configure these repository secrets in GitHub:
- `MAVEN_CENTRAL_USERNAME`: Central Portal User Token Username
- `MAVEN_CENTRAL_PASSWORD`: Central Portal User Token Password
- `SIGNING_KEY`: Armored GPG private key block
- `SIGNING_PASSWORD`: Passphrase for the GPG key

Triggering a GitHub Release automatically executes the `.github/workflows/publish.yml` workflow and deploys artifacts to Maven Central.

