#ifndef LIBHWMON_H
#define LIBHWMON_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Status Codes
typedef enum {
    HWMON_OK = 0,
    HWMON_ERROR_INVALID_ARGUMENT = 1,
    HWMON_ERROR_NOT_FOUND = 2,
    HWMON_ERROR_UNAUTHORIZED = 3,
    HWMON_ERROR_NOT_SUPPORTED = 4,
    HWMON_ERROR_INTERNAL = 5
} HwmonStatus;

// Error struct passed by caller to receive structured error details
typedef struct {
    HwmonStatus code;
    char message[1024];
} HwmonError;

// Opaque handles
typedef void* HwmonComputerHandle;
typedef void* HwmonHardwareHandle;
typedef void* HwmonSensorHandle;

typedef enum {
    HWMON_HW_UNKNOWN = 0,
    HWMON_HW_MOTHERBOARD = 1,
    HWMON_HW_SUPER_IO = 2,
    HWMON_HW_CPU = 3,
    HWMON_HW_RAM = 4,
    HWMON_HW_GPU_NVIDIA = 5,
    HWMON_HW_GPU_AMD = 6,
    HWMON_HW_GPU_INTEL = 7,
    HWMON_HW_STORAGE = 8,
    HWMON_HW_NETWORK = 9,
    HWMON_HW_CONTROLLER = 10,
    HWMON_HW_BATTERY = 11,
    HWMON_HW_EMBEDDED_CONTROLLER = 12
} HwmonHardwareType;

typedef enum {
    HWMON_SENSOR_VOLTAGE = 0,
    HWMON_SENSOR_CURRENT = 1,
    HWMON_SENSOR_POWER = 2,
    HWMON_SENSOR_CLOCK = 3,
    HWMON_SENSOR_TEMPERATURE = 4,
    HWMON_SENSOR_LOAD = 5,
    HWMON_SENSOR_FREQUENCY = 6,
    HWMON_SENSOR_FAN = 7,
    HWMON_SENSOR_FLOW = 8,
    HWMON_SENSOR_CONTROL = 9,
    HWMON_SENSOR_LEVEL = 10,
    HWMON_SENSOR_FACTOR = 11,
    HWMON_SENSOR_DATA = 12,
    HWMON_SENSOR_THROUGHPUT = 13,
    HWMON_SENSOR_TIME_SPAN = 14,
    HWMON_SENSOR_ENERGY = 15,
    HWMON_SENSOR_NOISE = 16,
    HWMON_SENSOR_HUMIDITY = 17
} HwmonSensorType;

typedef enum {
    HWMON_CONTROL_UNDEFINED = 0,
    HWMON_CONTROL_DEFAULT = 1,
    HWMON_CONTROL_SOFTWARE = 2
} HwmonControlMode;

typedef struct {
    bool enable_cpu;
    bool enable_gpu;
    bool enable_motherboard;
    bool enable_memory;
    bool enable_controller;
    bool enable_storage;
    bool enable_network;
    bool enable_battery;
} HwmonConfig;

// Flat telemetry record for single-pass batch sampling
typedef struct {
    char sensor_id[256];
    char hardware_id[256];
    HwmonSensorType sensor_type;
    float value;
    bool has_value;
    float min;
    bool has_min;
    float max;
    bool has_max;
    HwmonControlMode control_mode;
    float control_software_value;
    bool has_control_software_value;
} HwmonSensorTelemetryRecord;

// System / Environment
bool hwmon_is_administrator(void);

// Computer Lifecycle
HwmonStatus hwmon_computer_create(const HwmonConfig* config, HwmonComputerHandle* out_handle, HwmonError* err);
HwmonStatus hwmon_computer_open(HwmonComputerHandle computer, HwmonError* err);
HwmonStatus hwmon_computer_close(HwmonComputerHandle computer, HwmonError* err);
void hwmon_computer_free(HwmonComputerHandle computer);

// Update
HwmonStatus hwmon_computer_update(HwmonComputerHandle computer, HwmonError* err);
HwmonStatus hwmon_hardware_update(HwmonHardwareHandle hardware, HwmonError* err);

// High-Performance Batch Telemetry: Updates and fills telemetry in a single C-call
HwmonStatus hwmon_computer_get_telemetry_batch(
    HwmonComputerHandle computer,
    HwmonSensorTelemetryRecord* records_buffer,
    int32_t max_records,
    int32_t* out_records_written,
    HwmonError* err
);

// Computer Hardware Traversal
HwmonStatus hwmon_computer_get_hardware_count(HwmonComputerHandle computer, int32_t* out_count, HwmonError* err);
HwmonStatus hwmon_computer_get_hardware(HwmonComputerHandle computer, int32_t index, HwmonHardwareHandle* out_handle, HwmonError* err);

// Hardware Info
HwmonStatus hwmon_hardware_get_type(HwmonHardwareHandle hardware, HwmonHardwareType* out_type, HwmonError* err);
HwmonStatus hwmon_hardware_get_name(HwmonHardwareHandle hardware, char* buffer, int32_t max_len, int32_t* out_written, HwmonError* err);
HwmonStatus hwmon_hardware_get_identifier(HwmonHardwareHandle hardware, char* buffer, int32_t max_len, int32_t* out_written, HwmonError* err);
void hwmon_hardware_free(HwmonHardwareHandle hardware);

// Sub-hardware
HwmonStatus hwmon_hardware_get_subhardware_count(HwmonHardwareHandle hardware, int32_t* out_count, HwmonError* err);
HwmonStatus hwmon_hardware_get_subhardware(HwmonHardwareHandle hardware, int32_t index, HwmonHardwareHandle* out_handle, HwmonError* err);

// Sensors
HwmonStatus hwmon_hardware_get_sensor_count(HwmonHardwareHandle hardware, int32_t* out_count, HwmonError* err);
HwmonStatus hwmon_hardware_get_sensor(HwmonHardwareHandle hardware, int32_t index, HwmonSensorHandle* out_handle, HwmonError* err);
void hwmon_sensor_free(HwmonSensorHandle sensor);

// Sensor Info & Values
HwmonStatus hwmon_sensor_get_type(HwmonSensorHandle sensor, HwmonSensorType* out_type, HwmonError* err);
HwmonStatus hwmon_sensor_get_name(HwmonSensorHandle sensor, char* buffer, int32_t max_len, int32_t* out_written, HwmonError* err);
HwmonStatus hwmon_sensor_get_identifier(HwmonSensorHandle sensor, char* buffer, int32_t max_len, int32_t* out_written, HwmonError* err);
HwmonStatus hwmon_sensor_get_index(HwmonSensorHandle sensor, int32_t* out_index, HwmonError* err);

// Sensor Readings: *out_has_value is false if sensor value is null/unavailable
HwmonStatus hwmon_sensor_get_value(HwmonSensorHandle sensor, float* out_value, bool* out_has_value, HwmonError* err);
HwmonStatus hwmon_sensor_get_min(HwmonSensorHandle sensor, float* out_min, bool* out_has_value, HwmonError* err);
HwmonStatus hwmon_sensor_get_max(HwmonSensorHandle sensor, float* out_max, bool* out_has_value, HwmonError* err);

// Sensor Control (for controllable fans/pumps)
HwmonStatus hwmon_sensor_has_control(HwmonSensorHandle sensor, bool* out_has_control, HwmonError* err);
HwmonStatus hwmon_sensor_get_control_mode(HwmonSensorHandle sensor, HwmonControlMode* out_mode, HwmonError* err);
HwmonStatus hwmon_sensor_get_control_software_value(HwmonSensorHandle sensor, float* out_value, bool* out_has_value, HwmonError* err);
HwmonStatus hwmon_sensor_get_control_min_software_value(HwmonSensorHandle sensor, float* out_value, bool* out_has_value, HwmonError* err);
HwmonStatus hwmon_sensor_get_control_max_software_value(HwmonSensorHandle sensor, float* out_value, bool* out_has_value, HwmonError* err);
HwmonStatus hwmon_sensor_set_control_software(HwmonSensorHandle sensor, float value, HwmonError* err);
HwmonStatus hwmon_sensor_set_control_default(HwmonSensorHandle sensor, HwmonError* err);

#ifdef __cplusplus
}
#endif

#endif // LIBHWMON_H
