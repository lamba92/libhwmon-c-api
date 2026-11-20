package io.github.lamba92.libhwmon.sample

import io.github.lamba92.libhwmon.ControlMode
import io.github.lamba92.libhwmon.HardwareReading
import io.github.lamba92.libhwmon.HardwareType
import io.github.lamba92.libhwmon.HwmonClient
import io.github.lamba92.libhwmon.HwmonConfig
import io.github.lamba92.libhwmon.HwmonUnauthorizedException
import io.github.lamba92.libhwmon.SensorReading
import io.github.lamba92.libhwmon.SensorType
import kotlinx.coroutines.runBlocking
import kotlin.math.round

fun main() {
    println("================================================================================")
    println("        libhwmon-kotlin-api Hardware Telemetry Inspector & Diagnostic Tool      ")
    println("================================================================================")

    val config =
        HwmonConfig(
            enableCpu = true,
            enableGpu = true,
            enableMotherboard = true,
            enableMemory = true,
            enableController = true,
            enableStorage = true,
            enableNetwork = true,
            enableBattery = true,
        )

    val client =
        try {
            HwmonClient(config)
        } catch (e: HwmonUnauthorizedException) {
            println("\n[!] PERMISSION ERROR: Administrator privileges required.")
            println("    LibreHardwareMonitor requires elevated rights to load kernel drivers")
            println("    and query hardware sensors (WinRing0 / PawnIO).")
            println("    Please re-run this program from an elevated (Administrator) command prompt.")
            return
        } catch (e: Exception) {
            println("\n[!] FAILED to initialize HwmonClient: ${e.message}")
            return
        }

    client.use { hwmon ->
        println("\nProbing hardware subsystems and sampling initial telemetry...")
        val snapshot = runBlocking { hwmon.sampleTelemetry() }

        var totalHardwareCount = 0
        var totalSensorCount = 0
        var controllableSensorCount = 0

        fun countStats(hw: HardwareReading) {
            totalHardwareCount++
            totalSensorCount += hw.sensors.size
            controllableSensorCount += hw.sensors.count { it.controlMode != ControlMode.UNDEFINED }
            hw.subHardware.forEach { countStats(it) }
        }
        snapshot.hardware.forEach { countStats(it) }

        if (snapshot.hardware.isEmpty()) {
            println("\n[!] No hardware components detected.")
            return
        }

        println("\nDiscovered $totalHardwareCount hardware device(s) with $totalSensorCount sensor(s):\n")

        snapshot.hardware.forEachIndexed { index, hw ->
            val isLast = index == snapshot.hardware.size - 1
            printHardware(hw, prefix = "", isLast = isLast)
        }

        println("--------------------------------------------------------------------------------")
        println("SUMMARY:")
        println("  Total Hardware Components : $totalHardwareCount")
        println("  Total Sensors Discovered  : $totalSensorCount")
        println("  Controllable Actuators    : $controllableSensorCount")
        println("================================================================================")
    }
}

private fun printHardware(
    hw: HardwareReading,
    prefix: String,
    isLast: Boolean,
) {
    val branch = if (isLast) "\\-- " else "+-- "
    val childPrefix = prefix + if (isLast) "    " else "|   "

    val typeTag = formatHardwareType(hw.type)
    println("$prefix$branch$typeTag ${hw.name} [${hw.id}]")

    val totalChildren = hw.sensors.size + hw.subHardware.size
    var childIndex = 0

    for (sensor in hw.sensors) {
        childIndex++
        val isChildLast = childIndex == totalChildren
        printSensor(sensor, childPrefix, isChildLast)
    }

    for (sub in hw.subHardware) {
        childIndex++
        val isChildLast = childIndex == totalChildren
        printHardware(sub, childPrefix, isChildLast)
    }
}

private fun printSensor(
    sensor: SensorReading,
    prefix: String,
    isLast: Boolean,
) {
    val branch = if (isLast) "\\-- " else "+-- "
    val formattedValue = formatValue(sensor.value, sensor.type)
    val minMax =
        if (sensor.min != null && sensor.max != null) {
            " (min: ${formatValue(sensor.min, sensor.type)}, max: ${formatValue(sensor.max, sensor.type)})"
        } else {
            ""
        }
    val controlInfo =
        when (sensor.controlMode) {
            ControlMode.SOFTWARE -> " [CONTROL: Software @ ${sensor.controlSoftwareValue ?: 0f}%]"
            ControlMode.DEFAULT -> " [CONTROL: Default/Auto]"
            ControlMode.UNDEFINED -> ""
        }

    println("$prefix$branch[${sensor.type}] ${sensor.name}: $formattedValue$minMax$controlInfo (id: ${sensor.id})")
}

private fun formatHardwareType(type: HardwareType): String =
    when (type) {
        HardwareType.MOTHERBOARD -> "[Motherboard]"
        HardwareType.SUPER_IO -> "[Super I/O]"
        HardwareType.CPU -> "[CPU]"
        HardwareType.RAM -> "[RAM]"
        HardwareType.GPU_NVIDIA -> "[GPU: NVIDIA]"
        HardwareType.GPU_AMD -> "[GPU: AMD]"
        HardwareType.GPU_INTEL -> "[GPU: Intel]"
        HardwareType.STORAGE -> "[Storage]"
        HardwareType.NETWORK -> "[Network]"
        HardwareType.COOLER -> "[Cooler]"
        HardwareType.BATTERY -> "[Battery]"
        HardwareType.EMBEDDED_CONTROLLER -> "[Embedded Controller]"
        HardwareType.PSU -> "[PSU]"
        HardwareType.POWER_MONITOR -> "[Power Monitor]"
        HardwareType.CONTROLLER -> "[Controller]"
        HardwareType.UNKNOWN -> "[Unknown]"
    }

private fun formatValue(
    value: Float?,
    type: SensorType,
): String {
    if (value == null) return "N/A"
    val rounded = round(value * 100.0) / 100.0
    val unit = sensorUnit(type)
    return if (unit.isNotEmpty()) "$rounded $unit" else "$rounded"
}

private fun sensorUnit(type: SensorType): String =
    when (type) {
        SensorType.VOLTAGE -> "V"
        SensorType.CURRENT -> "A"
        SensorType.POWER -> "W"
        SensorType.CLOCK -> "MHz"
        SensorType.TEMPERATURE -> "°C"
        SensorType.LOAD -> "%"
        SensorType.FREQUENCY -> "Hz"
        SensorType.FAN -> "RPM"
        SensorType.FLOW -> "L/h"
        SensorType.CONTROL -> "%"
        SensorType.LEVEL -> "%"
        SensorType.FACTOR -> ""
        SensorType.DATA -> "GB"
        SensorType.THROUGHPUT -> "B/s"
        SensorType.TIME_SPAN -> "s"
        SensorType.ENERGY -> "mWh"
        SensorType.NOISE -> "dBA"
        SensorType.HUMIDITY -> "%"
    }
