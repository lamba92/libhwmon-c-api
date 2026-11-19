@file:OptIn(ExperimentalForeignApi::class)

package io.github.lamba92.libhwmon

import kotlinx.cinterop.*
import io.github.lamba92.libhwmon.interop.*
import io.github.lamba92.libhwmon.interop.HwmonConfig as CHwmonConfig

internal fun isAdministrator(): Boolean = hwmon_is_administrator()

internal data class BatchSensorRecord(
    val sensorId: SensorId,
    val hardwareId: HardwareId,
    val type: SensorType,
    val value: Float?,
    val min: Float?,
    val max: Float?,
    val controlMode: ControlMode,
    val controlSoftwareValue: Float?
)

internal fun MemScope.checkStatus(block: (CValuesRef<HwmonError>) -> HwmonStatus) {
    val err = alloc<HwmonError>()
    val status = block(err.ptr)
    if (status != HWMON_OK) {
        val msg = err.message.toKString()
        when (status) {
            HWMON_ERROR_INVALID_ARGUMENT -> throw HwmonInvalidArgumentException(msg)
            HWMON_ERROR_NOT_FOUND -> throw HwmonNotFoundException(msg)
            HWMON_ERROR_UNAUTHORIZED -> throw HwmonUnauthorizedException(msg)
            HWMON_ERROR_NOT_SUPPORTED -> throw HwmonNotSupportedException(msg)
            else -> throw HwmonException(HwmonErrorCode.INTERNAL, msg)
        }
    }
}

internal class NativeHwmonComputer(
    config: HwmonConfig = HwmonConfig()
) : AutoCloseable {

    val handle: HwmonComputerHandle

    init {
        val outHandle = memScoped {
            val cConfig = alloc<CHwmonConfig> {
                enable_cpu = config.enableCpu
                enable_gpu = config.enableGpu
                enable_motherboard = config.enableMotherboard
                enable_memory = config.enableMemory
                enable_controller = config.enableController
                enable_storage = config.enableStorage
                enable_network = config.enableNetwork
                enable_battery = config.enableBattery
            }
            val handlePtr = alloc<HwmonComputerHandleVar>()
            checkStatus { err -> hwmon_computer_create(cConfig.ptr, handlePtr.ptr, err) }
            handlePtr.value ?: error("HwmonComputer handle returned null after OK status")
        }
        handle = outHandle
        memScoped {
            checkStatus { err -> hwmon_computer_open(handle, err) }
        }
    }

    fun update() {
        memScoped {
            checkStatus { err -> hwmon_computer_update(handle, err) }
        }
    }

    fun getHardware(): List<NativeHwmonHardware> = memScoped {
        val countPtr = alloc<IntVar>()
        checkStatus { err -> hwmon_computer_get_hardware_count(handle, countPtr.ptr, err) }
        val count = countPtr.value
        val list = ArrayList<NativeHwmonHardware>(count)
        val hwPtr = alloc<HwmonHardwareHandleVar>()
        for (i in 0 until count) {
            checkStatus { err -> hwmon_computer_get_hardware(handle, i, hwPtr.ptr, err) }
            hwPtr.value?.let { list.add(NativeHwmonHardware(it)) }
        }
        list
    }

    fun getTelemetryBatch(maxRecords: Int = 1024): List<BatchSensorRecord> = memScoped {
        val records = allocArray<HwmonSensorTelemetryRecord>(maxRecords)
        val written = alloc<IntVar>()
        checkStatus { err ->
            hwmon_computer_get_telemetry_batch(handle, records, maxRecords, written.ptr, err)
        }
        val count = written.value
        val list = ArrayList<BatchSensorRecord>(count)
        for (i in 0 until count) {
            val rec = records[i]
            val sensorType = when (rec.sensor_type) {
                HWMON_SENSOR_VOLTAGE -> SensorType.VOLTAGE
                HWMON_SENSOR_CURRENT -> SensorType.CURRENT
                HWMON_SENSOR_POWER -> SensorType.POWER
                HWMON_SENSOR_CLOCK -> SensorType.CLOCK
                HWMON_SENSOR_TEMPERATURE -> SensorType.TEMPERATURE
                HWMON_SENSOR_LOAD -> SensorType.LOAD
                HWMON_SENSOR_FREQUENCY -> SensorType.FREQUENCY
                HWMON_SENSOR_FAN -> SensorType.FAN
                HWMON_SENSOR_FLOW -> SensorType.FLOW
                HWMON_SENSOR_CONTROL -> SensorType.CONTROL
                HWMON_SENSOR_LEVEL -> SensorType.LEVEL
                HWMON_SENSOR_FACTOR -> SensorType.FACTOR
                HWMON_SENSOR_DATA -> SensorType.DATA
                HWMON_SENSOR_THROUGHPUT -> SensorType.THROUGHPUT
                HWMON_SENSOR_TIME_SPAN -> SensorType.TIME_SPAN
                HWMON_SENSOR_ENERGY -> SensorType.ENERGY
                HWMON_SENSOR_NOISE -> SensorType.NOISE
                HWMON_SENSOR_HUMIDITY -> SensorType.HUMIDITY
                else -> SensorType.VOLTAGE
            }
            val controlMode = when (rec.control_mode) {
                HWMON_CONTROL_SOFTWARE -> ControlMode.SOFTWARE
                HWMON_CONTROL_DEFAULT -> ControlMode.DEFAULT
                else -> ControlMode.UNDEFINED
            }
            list.add(
                BatchSensorRecord(
                    sensorId = SensorId.from(rec.sensor_id.toKString()),
                    hardwareId = HardwareId.from(rec.hardware_id.toKString()),
                    type = sensorType,
                    value = if (rec.has_value) rec.value else null,
                    min = if (rec.has_min) rec.min else null,
                    max = if (rec.has_max) rec.max else null,
                    controlMode = controlMode,
                    controlSoftwareValue = if (rec.has_control_software_value) rec.control_software_value else null
                )
            )
        }
        list
    }

    override fun close() {
        memScoped {
            try {
                checkStatus { err -> hwmon_computer_close(handle, err) }
            } catch (_: Throwable) {
            }
        }
        hwmon_computer_free(handle)
    }
}

internal class NativeHwmonHardware(
    val handle: HwmonHardwareHandle
) : AutoCloseable {

    val type: HardwareType
        get() = memScoped {
            val typePtr = alloc<HwmonHardwareTypeVar>()
            checkStatus { err -> hwmon_hardware_get_type(handle, typePtr.ptr, err) }
            when (typePtr.value) {
                HWMON_HW_MOTHERBOARD -> HardwareType.MOTHERBOARD
                HWMON_HW_SUPER_IO -> HardwareType.SUPER_IO
                HWMON_HW_CPU -> HardwareType.CPU
                HWMON_HW_RAM -> HardwareType.RAM
                HWMON_HW_GPU_NVIDIA -> HardwareType.GPU_NVIDIA
                HWMON_HW_GPU_AMD -> HardwareType.GPU_AMD
                HWMON_HW_GPU_INTEL -> HardwareType.GPU_INTEL
                HWMON_HW_STORAGE -> HardwareType.STORAGE
                HWMON_HW_NETWORK -> HardwareType.NETWORK
                HWMON_HW_COOLER -> HardwareType.COOLER
                HWMON_HW_BATTERY -> HardwareType.BATTERY
                HWMON_HW_EMBEDDED_CONTROLLER -> HardwareType.EMBEDDED_CONTROLLER
                HWMON_HW_PSU -> HardwareType.PSU
                HWMON_HW_POWER_MONITOR -> HardwareType.POWER_MONITOR
                HWMON_HW_CONTROLLER -> HardwareType.CONTROLLER
                else -> HardwareType.UNKNOWN
            }
        }

    val name: String
        get() = memScoped {
            val buffer = allocArray<ByteVar>(512)
            val written = alloc<IntVar>()
            checkStatus { err -> hwmon_hardware_get_name(handle, buffer, 512, written.ptr, err) }
            buffer.toKString()
        }

    val id: HardwareId
        get() = memScoped {
            val buffer = allocArray<ByteVar>(512)
            val written = alloc<IntVar>()
            checkStatus { err -> hwmon_hardware_get_identifier(handle, buffer, 512, written.ptr, err) }
            HardwareId.from(buffer.toKString())
        }

    fun update() {
        memScoped {
            checkStatus { err -> hwmon_hardware_update(handle, err) }
        }
    }

    fun getSubHardware(): List<NativeHwmonHardware> = memScoped {
        val countPtr = alloc<IntVar>()
        checkStatus { err -> hwmon_hardware_get_subhardware_count(handle, countPtr.ptr, err) }
        val count = countPtr.value
        val list = ArrayList<NativeHwmonHardware>(count)
        val subPtr = alloc<HwmonHardwareHandleVar>()
        for (i in 0 until count) {
            checkStatus { err -> hwmon_hardware_get_subhardware(handle, i, subPtr.ptr, err) }
            subPtr.value?.let { list.add(NativeHwmonHardware(it)) }
        }
        list
    }

    fun getSensors(): List<NativeHwmonSensor> = memScoped {
        val countPtr = alloc<IntVar>()
        checkStatus { err -> hwmon_hardware_get_sensor_count(handle, countPtr.ptr, err) }
        val count = countPtr.value
        val list = ArrayList<NativeHwmonSensor>(count)
        val sensorPtr = alloc<HwmonSensorHandleVar>()
        for (i in 0 until count) {
            checkStatus { err -> hwmon_hardware_get_sensor(handle, i, sensorPtr.ptr, err) }
            sensorPtr.value?.let { list.add(NativeHwmonSensor(it)) }
        }
        list
    }

    override fun close() {
        hwmon_hardware_free(handle)
    }
}

internal class NativeHwmonSensor(
    val handle: HwmonSensorHandle
) : AutoCloseable {

    val type: SensorType
        get() = memScoped {
            val typePtr = alloc<HwmonSensorTypeVar>()
            checkStatus { err -> hwmon_sensor_get_type(handle, typePtr.ptr, err) }
            when (typePtr.value) {
                HWMON_SENSOR_VOLTAGE -> SensorType.VOLTAGE
                HWMON_SENSOR_CURRENT -> SensorType.CURRENT
                HWMON_SENSOR_POWER -> SensorType.POWER
                HWMON_SENSOR_CLOCK -> SensorType.CLOCK
                HWMON_SENSOR_TEMPERATURE -> SensorType.TEMPERATURE
                HWMON_SENSOR_LOAD -> SensorType.LOAD
                HWMON_SENSOR_FREQUENCY -> SensorType.FREQUENCY
                HWMON_SENSOR_FAN -> SensorType.FAN
                HWMON_SENSOR_FLOW -> SensorType.FLOW
                HWMON_SENSOR_CONTROL -> SensorType.CONTROL
                HWMON_SENSOR_LEVEL -> SensorType.LEVEL
                HWMON_SENSOR_FACTOR -> SensorType.FACTOR
                HWMON_SENSOR_DATA -> SensorType.DATA
                HWMON_SENSOR_THROUGHPUT -> SensorType.THROUGHPUT
                HWMON_SENSOR_TIME_SPAN -> SensorType.TIME_SPAN
                HWMON_SENSOR_ENERGY -> SensorType.ENERGY
                HWMON_SENSOR_NOISE -> SensorType.NOISE
                HWMON_SENSOR_HUMIDITY -> SensorType.HUMIDITY
                else -> SensorType.VOLTAGE
            }
        }

    val name: String
        get() = memScoped {
            val buffer = allocArray<ByteVar>(256)
            val written = alloc<IntVar>()
            checkStatus { err -> hwmon_sensor_get_name(handle, buffer, 256, written.ptr, err) }
            buffer.toKString()
        }

    val id: SensorId
        get() = memScoped {
            val buffer = allocArray<ByteVar>(256)
            val written = alloc<IntVar>()
            checkStatus { err -> hwmon_sensor_get_identifier(handle, buffer, 256, written.ptr, err) }
            SensorId.from(buffer.toKString())
        }

    val index: Int
        get() = memScoped {
            val indexPtr = alloc<IntVar>()
            checkStatus { err -> hwmon_sensor_get_index(handle, indexPtr.ptr, err) }
            indexPtr.value
        }

    val value: Float?
        get() = memScoped {
            val out = alloc<FloatVar>()
            val hasVal = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_get_value(handle, out.ptr, hasVal.ptr, err) }
            if (hasVal.value) out.value else null
        }

    val min: Float?
        get() = memScoped {
            val out = alloc<FloatVar>()
            val hasVal = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_get_min(handle, out.ptr, hasVal.ptr, err) }
            if (hasVal.value) out.value else null
        }

    val max: Float?
        get() = memScoped {
            val out = alloc<FloatVar>()
            val hasVal = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_get_max(handle, out.ptr, hasVal.ptr, err) }
            if (hasVal.value) out.value else null
        }

    val hasControl: Boolean
        get() = memScoped {
            val hasCtrl = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_has_control(handle, hasCtrl.ptr, err) }
            hasCtrl.value
        }

    val controlMode: ControlMode
        get() = memScoped {
            val modePtr = alloc<HwmonControlModeVar>()
            checkStatus { err -> hwmon_sensor_get_control_mode(handle, modePtr.ptr, err) }
            when (modePtr.value) {
                HWMON_CONTROL_SOFTWARE -> ControlMode.SOFTWARE
                HWMON_CONTROL_DEFAULT -> ControlMode.DEFAULT
                else -> ControlMode.UNDEFINED
            }
        }

    val controlSoftwareValue: Float?
        get() = memScoped {
            val out = alloc<FloatVar>()
            val hasVal = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_get_control_software_value(handle, out.ptr, hasVal.ptr, err) }
            if (hasVal.value) out.value else null
        }

    val controlLimits: ClosedFloatingPointRange<Float>?
        get() = memScoped {
            val minPtr = alloc<FloatVar>()
            val maxPtr = alloc<FloatVar>()
            val hasMin = alloc<BooleanVar>()
            val hasMax = alloc<BooleanVar>()
            checkStatus { err -> hwmon_sensor_get_control_min_software_value(handle, minPtr.ptr, hasMin.ptr, err) }
            checkStatus { err -> hwmon_sensor_get_control_max_software_value(handle, maxPtr.ptr, hasMax.ptr, err) }
            if (hasMin.value && hasMax.value) minPtr.value..maxPtr.value else null
        }

    fun setControlSoftware(value: Float) {
        memScoped {
            checkStatus { err -> hwmon_sensor_set_control_software(handle, value, err) }
        }
    }

    fun setControlDefault() {
        memScoped {
            checkStatus { err -> hwmon_sensor_set_control_default(handle, err) }
        }
    }

    override fun close() {
        hwmon_sensor_free(handle)
    }
}
