package io.github.lamba92.libhwmon

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * High-level, safe, coroutine-powered client for interacting with LibreHardwareMonitor on Windows.
 *
 * This client completely isolates native unmanaged memory, C-ABI pointers, and GCHandles under
 * the hood, exposing only immutable, type-safe, `@Serializable` models and coroutine flows.
 *
 * All ring-0 kernel driver interactions and hardware updates are thread-safe, serialized through
 * a process-global mutex, and executed on a dedicated single-threaded dispatcher context.
 *
 * ### Example Usage
 * ```kotlin
 * val client = HwmonClient()
 *
 * // Stream telemetry as a Flow
 * client.telemetryFlow(1.seconds).collect { snapshot ->
 *     snapshot.hardware.forEach { hw ->
 *         hw.sensors.forEach { sensor ->
 *             println("${sensor.name}: ${sensor.value}")
 *         }
 *     }
 * }
 *
 * // Override fan speed
 * client.setControlSoftware(fanSensorId, 80f)
 *
 * // Cleanup & restore BIOS defaults
 * client.close()
 * ```
 */
interface HwmonClient : AutoCloseable {

    /**
     * Inspects the connected PC hardware topology and returns a hierarchical tree of
     * [HardwareDescriptor]s and [SensorDescriptor]s.
     *
     * Hardware topology discovery is performed once and cached internally.
     *
     * @return A list of all detected top-level hardware components and their attached sensors.
     * @throws HwmonUnauthorizedException If the current process lacks required Administrator privileges.
     * @throws HwmonException If an unexpected internal error occurs during hardware enumeration.
     */
    suspend fun getHardwareTree(): List<HardwareDescriptor>

    /**
     * Performs a single update pass across all enabled hardware components and returns an
     * immutable [TelemetrySnapshot] containing current values, min/max limits, and control states.
     *
     * This operation uses optimized native batch memory copying to fetch all metrics in a single
     * C-call transition.
     *
     * @return A point-in-time [TelemetrySnapshot] for all monitored hardware and sensors.
     * @throws HwmonUnauthorizedException If the current process lacks required Administrator privileges.
     * @throws HwmonException If an unexpected internal error occurs during the update.
     */
    suspend fun sampleTelemetry(): TelemetrySnapshot

    /**
     * Creates a cold [Flow] that periodically polls hardware telemetry and emits continuous
     * [TelemetrySnapshot]s at the specified [interval].
     *
     * Hardware polling is performed on a dedicated background OS thread to ensure coroutine
     * and UI responsiveness are never blocked by kernel driver I/O.
     *
     * @param interval The delay duration between consecutive telemetry samples (defaults to 1 second).
     * @return A cold [Flow] emitting [TelemetrySnapshot]s until cancelled.
     */
    fun telemetryFlow(interval: Duration = 1.seconds): Flow<TelemetrySnapshot>

    /**
     * Sets a controllable fan or pump sensor to a fixed software duty cycle percentage.
     *
     * The sensor will switch to [ControlMode.SOFTWARE] and remain locked to this value
     * until [setControlDefault], [restoreAllControlDefaults], or [close] is invoked.
     *
     * @param sensorId The unique [SensorId] of the controllable sensor.
     * @param percent Target duty cycle percentage between `0.0f` and `100.0f`.
     * @throws HwmonNotFoundException If no sensor matching [sensorId] is found.
     * @throws HwmonNotSupportedException If the sensor does not support software duty cycle control.
     * @throws HwmonInvalidArgumentException If [percent] is outside the supported software limits.
     * @throws HwmonUnauthorizedException If the current process lacks Administrator privileges.
     */
    suspend fun setControlSoftware(sensorId: SensorId, percent: Float)

    /**
     * Restores a software-controlled fan or pump sensor back to its default hardware/BIOS
     * automated control curve ([ControlMode.DEFAULT]).
     *
     * @param sensorId The unique [SensorId] of the sensor to restore.
     * @throws HwmonNotFoundException If no sensor matching [sensorId] is found.
     * @throws HwmonNotSupportedException If the sensor does not support control.
     * @throws HwmonUnauthorizedException If the current process lacks Administrator privileges.
     */
    suspend fun setControlDefault(sensorId: SensorId)

    /**
     * Restores all fan and pump controls that were modified by this client back to their
     * default hardware/BIOS automated control curves.
     *
     * This is also executed automatically when [close] is called.
     *
     * @throws HwmonUnauthorizedException If the current process lacks Administrator privileges.
     */
    suspend fun restoreAllControlDefaults()

    /**
     * Factory companion object for instantiating [HwmonClient].
     */
    companion object {
        /**
         * Instantiates a new [HwmonClient] with the given [config].
         *
         * @param config Configuration options specifying which hardware subsystems to probe.
         * @return A configured [HwmonClient] instance.
         * @throws HwmonUnauthorizedException If the current process lacks Administrator privileges.
         */
        operator fun invoke(config: HwmonConfig = HwmonConfig()): HwmonClient =
            WindowsHwmonClient(config)
    }
}
