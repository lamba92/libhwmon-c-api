package io.github.lamba92.libhwmon

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration


/**
 * Windows-specific implementation of [HwmonClient] backed by LibreHardwareMonitor NativeAOT.
 *
 * Enforces Administrator elevation upon instantiation and serializes all unmanaged invocations
 * through a shared mutex on a dedicated OS thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
internal class WindowsHwmonClient(
    config: HwmonConfig = HwmonConfig()
) : HwmonClient {

    companion object {
        /** Process-wide mutex guaranteeing that only one coroutine interacts with ring-0 hardware drivers at a time. */
        private val globalMutex = Mutex()

        /** Dedicated single-threaded dispatcher pinning all unmanaged C-ABI calls to a stable OS thread. */
        private val hwmonDispatcher = newSingleThreadContext("HwmonIO")
    }

    init {
        if (!isAdministrator()) {
            throw HwmonUnauthorizedException(
                "LibreHardwareMonitor requires Administrator privileges to access ring-0 kernel drivers (WinRing0/PawnIO) and hardware sensors."
            )
        }
    }

    private val native = NativeHwmonComputer(config)
    private val controlledSensors = mutableSetOf<SensorId>()
    private var cachedHardwareTree: List<HardwareDescriptor>? = null

    override suspend fun getHardwareTree(): List<HardwareDescriptor> = withContext(hwmonDispatcher) {
        globalMutex.withLock {
            cachedHardwareTree?.let { return@withLock it }
            native.update()
            val list = mutableListOf<HardwareDescriptor>()
            for (hw in native.getHardware()) {
                list.add(readHardwareDescriptor(hw))
                hw.close()
            }
            cachedHardwareTree = list
            list
        }
    }

    override suspend fun sampleTelemetry(): TelemetrySnapshot = withContext(hwmonDispatcher) {
        globalMutex.withLock {
            val tree = cachedHardwareTree ?: run {
                native.update()
                val list = mutableListOf<HardwareDescriptor>()
                for (hw in native.getHardware()) {
                    list.add(readHardwareDescriptor(hw))
                    hw.close()
                }
                cachedHardwareTree = list
                list
            }

            // Single high-performance C-call batch update
            val batchRecords = native.getTelemetryBatch(maxRecords = 2048)
            val recordMap = batchRecords.associateBy { it.sensorId }

            fun buildReading(desc: HardwareDescriptor): HardwareReading {
                val sensorReadings = desc.sensors.map { sDesc ->
                    val rec = recordMap[sDesc.id]
                    SensorReading(
                        id = sDesc.id,
                        name = sDesc.name,
                        type = sDesc.type,
                        value = rec?.value,
                        min = rec?.min,
                        max = rec?.max,
                        controlMode = rec?.controlMode ?: ControlMode.UNDEFINED,
                        controlSoftwareValue = rec?.controlSoftwareValue
                    )
                }
                val subReadings = desc.subHardware.map { buildReading(it) }
                return HardwareReading(
                    id = desc.id,
                    name = desc.name,
                    type = desc.type,
                    sensors = sensorReadings,
                    subHardware = subReadings
                )
            }

            TelemetrySnapshot(tree.map { buildReading(it) })
        }
    }

    override fun telemetryFlow(interval: Duration): Flow<TelemetrySnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(sampleTelemetry())
            delay(interval)
        }
    }

    override suspend fun setControlSoftware(sensorId: SensorId, percent: Float): Unit =
        withContext(hwmonDispatcher) {
            globalMutex.withLock {
                findSensor(sensorId) { sensor ->
                    sensor.setControlSoftware(percent)
                    controlledSensors.add(sensorId)
                } ?: throw HwmonNotFoundException("Sensor '${sensorId.value}' not found")
            }
        }

    override suspend fun setControlDefault(sensorId: SensorId): Unit =
        withContext(hwmonDispatcher) {
            globalMutex.withLock {
                findSensor(sensorId) { sensor ->
                    sensor.setControlDefault()
                    controlledSensors.remove(sensorId)
                } ?: throw HwmonNotFoundException("Sensor '${sensorId.value}' not found")
            }
        }

    override suspend fun restoreAllControlDefaults(): Unit =
        withContext(hwmonDispatcher) {
            globalMutex.withLock {
                for (id in controlledSensors.toList()) {
                    findSensor(id) { it.setControlDefault() }
                }
                controlledSensors.clear()
            }
        }

    override fun close() {
        runBlocking(hwmonDispatcher) {
            globalMutex.withLock {
                try {
                    for (id in controlledSensors) {
                        findSensor(id) { it.setControlDefault() }
                    }
                    controlledSensors.clear()
                } catch (_: Throwable) {
                }
                native.close()
            }
        }
    }

    private inline fun <T> findSensor(sensorId: SensorId, action: (NativeHwmonSensor) -> T): T? {
        val hardwareList = native.getHardware()
        try {
            for (hw in hardwareList) {
                val found = searchHardwareSensor(hw, sensorId, action)
                if (found != null) return found
            }
            return null
        } finally {
            for (hw in hardwareList) {
                hw.close()
            }
        }
    }

    private inline fun <T> searchHardwareSensor(
        hw: NativeHwmonHardware,
        sensorId: SensorId,
        action: (NativeHwmonSensor) -> T
    ): T? {
        val sensors = hw.getSensors()
        try {
            for (sensor in sensors) {
                if (sensor.id == sensorId) {
                    return action(sensor)
                }
            }
        } finally {
            for (sensor in sensors) {
                sensor.close()
            }
        }

        val subList = hw.getSubHardware()
        try {
            for (sub in subList) {
                val found = searchHardwareSensor(sub, sensorId, action)
                if (found != null) return found
            }
        } finally {
            for (sub in subList) {
                sub.close()
            }
        }
        return null
    }

    private fun readHardwareDescriptor(hw: NativeHwmonHardware): HardwareDescriptor {
        val sensors = hw.getSensors()
        val sensorDescriptors = sensors.map { s ->
            SensorDescriptor(
                id = s.id,
                name = s.name,
                index = s.index,
                type = s.type,
                hasControl = s.hasControl
            ).also { s.close() }
        }

        val subList = hw.getSubHardware()
        val subDescriptors = subList.map { sub ->
            readHardwareDescriptor(sub).also { sub.close() }
        }

        return HardwareDescriptor(
            id = hw.id,
            name = hw.name,
            type = hw.type,
            sensors = sensorDescriptors,
            subHardware = subDescriptors
        )
    }

    }
}
