package io.github.lamba92.libhwmon

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Standard error codes returned across the native C ABI boundary.
 */
@Serializable
enum class HwmonErrorCode {
    /** An invalid argument or null pointer was supplied to the native API. */
    INVALID_ARGUMENT,

    /** The requested hardware or sensor was not found. */
    NOT_FOUND,

    /** The process lacks Administrator privileges required to load kernel drivers. */
    UNAUTHORIZED,

    /** The operation or control capability is not supported on the target sensor. */
    NOT_SUPPORTED,

    /** An unexpected internal error occurred within LibreHardwareMonitor or the native bridge. */
    INTERNAL,
}

/**
 * Base exception for all errors originating from LibreHardwareMonitor or the native bridge.
 *
 * @property code The specific error code classification.
 * @property message A descriptive error message detailing the failure.
 * @property cause An optional underlying cause.
 */
open class HwmonException(
    val code: HwmonErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("[$code] $message", cause)

/**
 * Thrown when the current process lacks required Administrator privileges to communicate with
 * ring-0 kernel drivers (such as WinRing0 or PawnIO).
 */
class HwmonUnauthorizedException(
    message: String,
) : HwmonException(HwmonErrorCode.UNAUTHORIZED, message)

/**
 * Thrown when a requested hardware device or sensor identifier cannot be located.
 */
class HwmonNotFoundException(
    message: String,
) : HwmonException(HwmonErrorCode.NOT_FOUND, message)

/**
 * Thrown when an invalid argument or value is supplied to an API call.
 */
class HwmonInvalidArgumentException(
    message: String,
) : HwmonException(HwmonErrorCode.INVALID_ARGUMENT, message)

/**
 * Thrown when attempting an unsupported operation on a sensor (e.g. software duty control on a read-only fan).
 */
class HwmonNotSupportedException(
    message: String,
) : HwmonException(HwmonErrorCode.NOT_SUPPORTED, message)

/**
 * Type-safe value class representing a unique hardware device identifier (e.g. `/amdcpu/0`, `/gpu-nvidia/0`).
 *
 * Prevents accidental passing of arbitrary or malformed strings.
 *
 * @property value The raw string identifier assigned by LibreHardwareMonitor.
 */
@Serializable
@JvmInline
value class HardwareId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "HardwareId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        /**
         * Creates a [HardwareId] from a raw string, trimming leading and trailing whitespace.
         */
        fun from(string: String): HardwareId = HardwareId(string.trim())
    }
}

/**
 * Type-safe value class representing a unique sensor identifier (e.g. `/amdcpu/0/temperature/2`).
 *
 * Prevents accidental passing of arbitrary or malformed strings.
 *
 * @property value The raw string identifier assigned by LibreHardwareMonitor.
 */
@Serializable
@JvmInline
value class SensorId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SensorId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        /**
         * Creates a [SensorId] from a raw string, trimming leading and trailing whitespace.
         */
        fun from(string: String): SensorId = SensorId(string.trim())
    }
}

/**
 * Categories of physical or logical hardware supported by LibreHardwareMonitor.
 */
@Serializable
enum class HardwareType {
    UNKNOWN,
    MOTHERBOARD,
    SUPER_IO,
    CPU,
    RAM,
    GPU_NVIDIA,
    GPU_AMD,
    GPU_INTEL,
    STORAGE,
    NETWORK,
    COOLER,
    BATTERY,
    EMBEDDED_CONTROLLER,
    PSU,
    POWER_MONITOR,
    CONTROLLER,
}

/**
 * Categories of physical telemetry measurements collected by sensors.
 */
@Serializable
enum class SensorType {
    VOLTAGE,
    CURRENT,
    POWER,
    CLOCK,
    TEMPERATURE,
    LOAD,
    FREQUENCY,
    FAN,
    FLOW,
    CONTROL,
    LEVEL,
    FACTOR,
    DATA,
    THROUGHPUT,
    TIME_SPAN,
    ENERGY,
    NOISE,
    HUMIDITY,
}

/**
 * Operating control mode of a controllable sensor (such as a fan header or liquid pump).
 */
@Serializable
enum class ControlMode {
    /** The control mode is unknown or unmanaged. */
    UNDEFINED,

    /** The sensor follows hardware/BIOS automated fan curve control. */
    DEFAULT,

    /** The sensor is locked to a fixed software duty cycle override. */
    SOFTWARE,
}

/**
 * Configuration options determining which hardware subsystems LibreHardwareMonitor will probe on startup.
 *
 * @property enableCpu Probes CPU cores, package sensors, temperatures, and powers.
 * @property enableGpu Probes dedicated and integrated GPUs via NVAPI, ADL, or Intel IGCL.
 * @property enableMotherboard Probes motherboard chipsets and Super I/O chips (e.g. Nuvoton, ITE, Fintek).
 * @property enableMemory Probes system memory / RAM modules.
 * @property enableController Probes dedicated fan/RGB controllers.
 * @property enableStorage Probes NVMe, SSD, and HDD drives via SMART/WMI.
 * @property enableNetwork Probes network adapter throughput and packet metrics via WMI.
 * @property enableBattery Probes laptop and UPS battery telemetry via WMI.
 */
@Serializable
data class HwmonConfig(
    val enableCpu: Boolean = true,
    val enableGpu: Boolean = true,
    val enableMotherboard: Boolean = true,
    val enableMemory: Boolean = false,
    val enableController: Boolean = true,
    val enableStorage: Boolean = true,
    val enableNetwork: Boolean = false,
    val enableBattery: Boolean = false,
)

/**
 * Static metadata descriptor describing a sensor discovered on a hardware component.
 *
 * @property id The unique [SensorId] identifying this sensor.
 * @property name Human-readable sensor name (e.g. "Core (Tctl/Tdie)", "CPU Fan").
 * @property index Sensor index within its hardware parent.
 * @property type The physical measurement category of this sensor.
 * @property hasControl True if this sensor supports software control (e.g. PWM fan speed override).
 */
@Serializable
data class SensorDescriptor(
    val id: SensorId,
    val name: String,
    val index: Int,
    val type: SensorType,
    val hasControl: Boolean,
)

/**
 * Static metadata descriptor describing a hardware component and its attached sensors.
 *
 * @property id The unique [HardwareId] identifying this component.
 * @property name Human-readable hardware name (e.g. "AMD Ryzen 9 7950X", "NVIDIA GeForce RTX 4090").
 * @property type The hardware classification category.
 * @property sensors List of sensors directly attached to this hardware.
 * @property subHardware List of child hardware sub-components (e.g. Super I/O under a Motherboard).
 */
@Serializable
data class HardwareDescriptor(
    val id: HardwareId,
    val name: String,
    val type: HardwareType,
    val sensors: List<SensorDescriptor> = emptyList(),
    val subHardware: List<HardwareDescriptor> = emptyList(),
)

/**
 * Live telemetry reading for a single sensor collected during an update cycle.
 *
 * @property id The unique [SensorId] of the sensor.
 * @property name Human-readable sensor name.
 * @property type The physical measurement category.
 * @property value Current instantaneous value, or `null` if temporarily unreadable or disconnected.
 * @property min Minimum value recorded since monitor initialization.
 * @property max Maximum value recorded since monitor initialization.
 * @property controlMode Active control mode if this sensor is controllable.
 * @property controlSoftwareValue Active software target duty cycle (0.0..100.0) if in [ControlMode.SOFTWARE].
 */
@Serializable
data class SensorReading(
    val id: SensorId,
    val name: String,
    val type: SensorType,
    val value: Float?,
    val min: Float? = null,
    val max: Float? = null,
    val controlMode: ControlMode = ControlMode.UNDEFINED,
    val controlSoftwareValue: Float? = null,
)

/**
 * Live telemetry readings for a hardware component and all its sensors at a specific point in time.
 *
 * @property id The unique [HardwareId] of the hardware component.
 * @property name Human-readable hardware name.
 * @property type The hardware classification category.
 * @property sensors Current readings for all sensors attached to this hardware.
 * @property subHardware Current readings for all child sub-hardware components.
 */
@Serializable
data class HardwareReading(
    val id: HardwareId,
    val name: String,
    val type: HardwareType,
    val sensors: List<SensorReading> = emptyList(),
    val subHardware: List<HardwareReading> = emptyList(),
)

/**
 * An immutable, point-in-time telemetry snapshot of the entire PC hardware monitoring tree.
 *
 * @property hardware Telemetry readings for all monitored top-level hardware components.
 */
@Serializable
data class TelemetrySnapshot(
    val hardware: List<HardwareReading>,
)

/**
 * Request payload to adjust a controllable fan or pump to a specified software duty cycle percentage.
 *
 * @property sensorId The target sensor's unique identifier.
 * @property percent Target software duty cycle percentage between `0.0f` and `100.0f`.
 */
@Serializable
data class FanControlRequest(
    val sensorId: SensorId,
    val percent: Float,
)
