using System.Runtime.InteropServices;
using System.Text;
using LibreHardwareMonitor.Hardware;

namespace Lamba92.LibHwmon;

public static unsafe class NativeExports
{
    private static IntPtr AllocateHandle(object? obj)
    {
        if (obj == null) return IntPtr.Zero;
        return GCHandle.ToIntPtr(GCHandle.Alloc(obj));
    }

    private static T? GetTarget<T>(IntPtr handle) where T : class
    {
        if (handle == IntPtr.Zero) return null;
        var gcHandle = GCHandle.FromIntPtr(handle);
        return gcHandle.Target as T;
    }

    private static void FreeHandle(IntPtr handle)
    {
        if (handle == IntPtr.Zero) return;
        var gcHandle = GCHandle.FromIntPtr(handle);
        if (gcHandle.IsAllocated) gcHandle.Free();
    }

    private static void SetError(HwmonError* err, HwmonStatus code, string message)
    {
        if (err == null) return;
        err->Code = code;
        int maxBytes = 1023;
        int byteCount = Encoding.UTF8.GetByteCount(message);
        if (byteCount > maxBytes) byteCount = maxBytes;

        fixed (char* pChars = message)
        {
            Encoding.UTF8.GetBytes(pChars, message.Length, err->Message, byteCount);
        }
        err->Message[byteCount] = 0;
    }

    private static void HandleException(HwmonError* err, Exception ex)
    {
        var code = ex switch
        {
            ArgumentException => HwmonStatus.ErrorInvalidArgument,
            UnauthorizedAccessException => HwmonStatus.ErrorUnauthorized,
            NotSupportedException => HwmonStatus.ErrorNotSupported,
            KeyNotFoundException => HwmonStatus.ErrorNotFound,
            _ => HwmonStatus.ErrorInternal
        };
        SetError(err, code, $"{ex.GetType().Name}: {ex.Message}");
    }

    private static int CopyStringToUtf8Buffer(string? source, byte* buffer, int maxLen)
    {
        if (source == null || buffer == null || maxLen <= 0) return 0;
        int byteCount = Encoding.UTF8.GetByteCount(source);
        if (byteCount >= maxLen) byteCount = maxLen - 1;
        fixed (char* pChars = source)
        {
            Encoding.UTF8.GetBytes(pChars, source.Length, buffer, byteCount);
        }
        buffer[byteCount] = 0;
        return byteCount;
    }

    private sealed class UpdateVisitor : IVisitor
    {
        public static readonly UpdateVisitor Instance = new();

        public void VisitComputer(IComputer computer) => computer.Traverse(this);

        public void VisitHardware(IHardware hardware)
        {
            hardware.Update();
            foreach (var sub in hardware.SubHardware)
            {
                sub.Accept(this);
            }
        }

        public void VisitSensor(ISensor sensor) { }
        public void VisitParameter(IParameter parameter) { }
    }

    // Computer Lifecycle
    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_create")]
    public static HwmonStatus ComputerCreate(HwmonConfig* config, IntPtr* outHandle, HwmonError* err)
    {
        if (outHandle == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var computer = new Computer();
            if (config != null)
            {
                computer.IsCpuEnabled = config->EnableCpu;
                computer.IsGpuEnabled = config->EnableGpu;
                computer.IsMotherboardEnabled = config->EnableMotherboard;
                computer.IsMemoryEnabled = config->EnableMemory;
                computer.IsControllerEnabled = config->EnableController;
                computer.IsStorageEnabled = config->EnableStorage;
                computer.IsNetworkEnabled = config->EnableNetwork;
                computer.IsBatteryEnabled = config->EnableBattery;
            }
            *outHandle = AllocateHandle(computer);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            *outHandle = IntPtr.Zero;
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_open")]
    public static HwmonStatus ComputerOpen(IntPtr handle, HwmonError* err)
    {
        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            computer.Open();
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_close")]
    public static HwmonStatus ComputerClose(IntPtr handle, HwmonError* err)
    {
        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            computer.Close();
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_free")]
    public static void ComputerFree(IntPtr handle)
    {
        try
        {
            var computer = GetTarget<Computer>(handle);
            computer?.Close();
        }
        finally
        {
            FreeHandle(handle);
        }
    }

    // Updates
    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_update")]
    public static HwmonStatus ComputerUpdate(IntPtr handle, HwmonError* err)
    {
        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            computer.Accept(UpdateVisitor.Instance);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_update")]
    public static HwmonStatus HardwareUpdate(IntPtr handle, HwmonError* err)
    {
        try
        {
            var hardware = GetTarget<IHardware>(handle);
            if (hardware == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            hardware.Update();
            foreach (var sub in hardware.SubHardware)
            {
                sub.Accept(UpdateVisitor.Instance);
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    // System / Environment
    [UnmanagedCallersOnly(EntryPoint = "hwmon_is_administrator")]
    [return: MarshalAs(UnmanagedType.I1)]
    public static bool IsAdministrator()
    {
        try
        {
            if (OperatingSystem.IsWindows())
            {
                using var identity = System.Security.Principal.WindowsIdentity.GetCurrent();
                var principal = new System.Security.Principal.WindowsPrincipal(identity);
                return principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
            }
            return false;
        }
        catch
        {
            return false;
        }
    }

    // High-Performance Batch Telemetry
    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_get_telemetry_batch")]
    public static HwmonStatus ComputerGetTelemetryBatch(
        IntPtr handle,
        HwmonSensorTelemetryRecord* recordsBuffer,
        int maxRecords,
        int* outRecordsWritten,
        HwmonError* err)
    {
        if (recordsBuffer == null || maxRecords <= 0 || outRecordsWritten == null)
            return HwmonStatus.ErrorInvalidArgument;

        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }

            computer.Accept(UpdateVisitor.Instance);

            int count = 0;
            foreach (var hardware in computer.Hardware)
            {
                CollectTelemetryRecords(hardware, recordsBuffer, maxRecords, ref count);
                if (count >= maxRecords) break;
            }

            *outRecordsWritten = count;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    private static void CollectTelemetryRecords(
        IHardware hardware,
        HwmonSensorTelemetryRecord* recordsBuffer,
        int maxRecords,
        ref int count)
    {
        string hwId = hardware.Identifier.ToString();

        foreach (var sensor in hardware.Sensors)
        {
            if (count >= maxRecords) return;

            HwmonSensorTelemetryRecord* rec = &recordsBuffer[count];
            CopyStringToUtf8Buffer(sensor.Identifier.ToString(), rec->SensorId, 256);
            CopyStringToUtf8Buffer(hwId, rec->HardwareId, 256);

            rec->SensorType = sensor.SensorType switch
            {
                SensorType.Voltage => HwmonSensorType.Voltage,
                SensorType.Current => HwmonSensorType.Current,
                SensorType.Power => HwmonSensorType.Power,
                SensorType.Clock => HwmonSensorType.Clock,
                SensorType.Temperature => HwmonSensorType.Temperature,
                SensorType.Load => HwmonSensorType.Load,
                SensorType.Frequency => HwmonSensorType.Frequency,
                SensorType.Fan => HwmonSensorType.Fan,
                SensorType.Flow => HwmonSensorType.Flow,
                SensorType.Control => HwmonSensorType.Control,
                SensorType.Level => HwmonSensorType.Level,
                SensorType.Factor => HwmonSensorType.Factor,
                SensorType.Data => HwmonSensorType.Data,
                SensorType.Throughput => HwmonSensorType.Throughput,
                SensorType.TimeSpan => HwmonSensorType.TimeSpan,
                SensorType.Energy => HwmonSensorType.Energy,
                SensorType.Noise => HwmonSensorType.Noise,
                SensorType.Humidity => HwmonSensorType.Humidity,
                _ => HwmonSensorType.Voltage
            };

            if (sensor.Value is float val)
            {
                rec->Value = val;
                rec->HasValue = true;
            }
            else
            {
                rec->Value = 0f;
                rec->HasValue = false;
            }

            if (sensor.Min is float minVal)
            {
                rec->Min = minVal;
                rec->HasMin = true;
            }
            else
            {
                rec->Min = 0f;
                rec->HasMin = false;
            }

            if (sensor.Max is float maxVal)
            {
                rec->Max = maxVal;
                rec->HasMax = true;
            }
            else
            {
                rec->Max = 0f;
                rec->HasMax = false;
            }

            var ctrl = sensor.Control;
            if (ctrl != null)
            {
                rec->ControlMode = ctrl.ControlMode switch
                {
                    ControlMode.Default => HwmonControlMode.Default,
                    ControlMode.Software => HwmonControlMode.Software,
                    _ => HwmonControlMode.Undefined
                };
                rec->ControlSoftwareValue = ctrl.SoftwareValue;
                rec->HasControlSoftwareValue = true;
            }
            else
            {
                rec->ControlMode = HwmonControlMode.Undefined;
                rec->ControlSoftwareValue = 0f;
                rec->HasControlSoftwareValue = false;
            }

            count++;
        }

        foreach (var sub in hardware.SubHardware)
        {
            if (count >= maxRecords) return;
            CollectTelemetryRecords(sub, recordsBuffer, maxRecords, ref count);
        }
    }

    // Hardware Traversal
    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_get_hardware_count")]
    public static HwmonStatus ComputerGetHardwareCount(IntPtr handle, int* outCount, HwmonError* err)
    {
        if (outCount == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outCount = computer.Hardware.Count;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_computer_get_hardware")]
    public static HwmonStatus ComputerGetHardware(IntPtr handle, int index, IntPtr* outHandle, HwmonError* err)
    {
        if (outHandle == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var computer = GetTarget<Computer>(handle);
            if (computer == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Computer handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (index < 0 || index >= computer.Hardware.Count)
            {
                SetError(err, HwmonStatus.ErrorNotFound, $"Hardware index {index} out of range");
                return HwmonStatus.ErrorNotFound;
            }
            *outHandle = AllocateHandle(computer.Hardware[index]);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_type")]
    public static HwmonStatus HardwareGetType(IntPtr handle, HwmonHardwareType* outType, HwmonError* err)
    {
        if (outType == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outType = hw.HardwareType switch
            {
                HardwareType.Motherboard => HwmonHardwareType.Motherboard,
                HardwareType.SuperIO => HwmonHardwareType.SuperIO,
                HardwareType.Cpu => HwmonHardwareType.Cpu,
                HardwareType.Memory => HwmonHardwareType.Ram,
                HardwareType.GpuNvidia => HwmonHardwareType.GpuNvidia,
                HardwareType.GpuAmd => HwmonHardwareType.GpuAmd,
                HardwareType.GpuIntel => HwmonHardwareType.GpuIntel,
                HardwareType.Storage => HwmonHardwareType.Storage,
                HardwareType.Network => HwmonHardwareType.Network,
                HardwareType.Controller => HwmonHardwareType.Controller,
                HardwareType.Battery => HwmonHardwareType.Battery,
                HardwareType.EmbeddedController => HwmonHardwareType.EmbeddedController,
                _ => HwmonHardwareType.Unknown
            };
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_name")]
    public static HwmonStatus HardwareGetName(IntPtr handle, byte* buffer, int maxLen, int* outWritten, HwmonError* err)
    {
        if (buffer == null || maxLen <= 0) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            int written = CopyStringToUtf8Buffer(hw.Name, buffer, maxLen);
            if (outWritten != null) *outWritten = written;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_identifier")]
    public static HwmonStatus HardwareGetIdentifier(IntPtr handle, byte* buffer, int maxLen, int* outWritten, HwmonError* err)
    {
        if (buffer == null || maxLen <= 0) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            int written = CopyStringToUtf8Buffer(hw.Identifier.ToString(), buffer, maxLen);
            if (outWritten != null) *outWritten = written;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_free")]
    public static void HardwareFree(IntPtr handle) => FreeHandle(handle);

    // Sub-hardware
    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_subhardware_count")]
    public static HwmonStatus HardwareGetSubHardwareCount(IntPtr handle, int* outCount, HwmonError* err)
    {
        if (outCount == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outCount = hw.SubHardware.Length;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_subhardware")]
    public static HwmonStatus HardwareGetSubHardware(IntPtr handle, int index, IntPtr* outHandle, HwmonError* err)
    {
        if (outHandle == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (index < 0 || index >= hw.SubHardware.Length)
            {
                SetError(err, HwmonStatus.ErrorNotFound, $"SubHardware index {index} out of range");
                return HwmonStatus.ErrorNotFound;
            }
            *outHandle = AllocateHandle(hw.SubHardware[index]);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    // Sensors
    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_sensor_count")]
    public static HwmonStatus HardwareGetSensorCount(IntPtr handle, int* outCount, HwmonError* err)
    {
        if (outCount == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outCount = hw.Sensors.Length;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_hardware_get_sensor")]
    public static HwmonStatus HardwareGetSensor(IntPtr handle, int index, IntPtr* outHandle, HwmonError* err)
    {
        if (outHandle == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var hw = GetTarget<IHardware>(handle);
            if (hw == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Hardware handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (index < 0 || index >= hw.Sensors.Length)
            {
                SetError(err, HwmonStatus.ErrorNotFound, $"Sensor index {index} out of range");
                return HwmonStatus.ErrorNotFound;
            }
            *outHandle = AllocateHandle(hw.Sensors[index]);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_free")]
    public static void SensorFree(IntPtr handle) => FreeHandle(handle);

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_type")]
    public static HwmonStatus SensorGetType(IntPtr handle, HwmonSensorType* outType, HwmonError* err)
    {
        if (outType == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outType = sensor.SensorType switch
            {
                SensorType.Voltage => HwmonSensorType.Voltage,
                SensorType.Current => HwmonSensorType.Current,
                SensorType.Power => HwmonSensorType.Power,
                SensorType.Clock => HwmonSensorType.Clock,
                SensorType.Temperature => HwmonSensorType.Temperature,
                SensorType.Load => HwmonSensorType.Load,
                SensorType.Frequency => HwmonSensorType.Frequency,
                SensorType.Fan => HwmonSensorType.Fan,
                SensorType.Flow => HwmonSensorType.Flow,
                SensorType.Control => HwmonSensorType.Control,
                SensorType.Level => HwmonSensorType.Level,
                SensorType.Factor => HwmonSensorType.Factor,
                SensorType.Data => HwmonSensorType.Data,
                SensorType.Throughput => HwmonSensorType.Throughput,
                SensorType.TimeSpan => HwmonSensorType.TimeSpan,
                SensorType.Energy => HwmonSensorType.Energy,
                SensorType.Noise => HwmonSensorType.Noise,
                SensorType.Humidity => HwmonSensorType.Humidity,
                _ => HwmonSensorType.Voltage
            };
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_name")]
    public static HwmonStatus SensorGetName(IntPtr handle, byte* buffer, int maxLen, int* outWritten, HwmonError* err)
    {
        if (buffer == null || maxLen <= 0) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            int written = CopyStringToUtf8Buffer(sensor.Name, buffer, maxLen);
            if (outWritten != null) *outWritten = written;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_identifier")]
    public static HwmonStatus SensorGetIdentifier(IntPtr handle, byte* buffer, int maxLen, int* outWritten, HwmonError* err)
    {
        if (buffer == null || maxLen <= 0) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            int written = CopyStringToUtf8Buffer(sensor.Identifier.ToString(), buffer, maxLen);
            if (outWritten != null) *outWritten = written;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_index")]
    public static HwmonStatus SensorGetIndex(IntPtr handle, int* outIndex, HwmonError* err)
    {
        if (outIndex == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outIndex = sensor.Index;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_value")]
    public static HwmonStatus SensorGetValue(IntPtr handle, float* outValue, bool* outHasValue, HwmonError* err)
    {
        if (outValue == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (sensor.Value is float val)
            {
                *outValue = val;
                *outHasValue = true;
            }
            else
            {
                *outValue = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_min")]
    public static HwmonStatus SensorGetMin(IntPtr handle, float* outMin, bool* outHasValue, HwmonError* err)
    {
        if (outMin == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (sensor.Min is float val)
            {
                *outMin = val;
                *outHasValue = true;
            }
            else
            {
                *outMin = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_max")]
    public static HwmonStatus SensorGetMax(IntPtr handle, float* outMax, bool* outHasValue, HwmonError* err)
    {
        if (outMax == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            if (sensor.Max is float val)
            {
                *outMax = val;
                *outHasValue = true;
            }
            else
            {
                *outMax = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    // Control
    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_has_control")]
    public static HwmonStatus SensorHasControl(IntPtr handle, bool* outHasControl, HwmonError* err)
    {
        if (outHasControl == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            *outHasControl = sensor.Control != null;
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_control_mode")]
    public static HwmonStatus SensorGetControlMode(IntPtr handle, HwmonControlMode* outMode, HwmonError* err)
    {
        if (outMode == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control == null)
            {
                *outMode = HwmonControlMode.Undefined;
            }
            else
            {
                *outMode = control.ControlMode switch
                {
                    ControlMode.Default => HwmonControlMode.Default,
                    ControlMode.Software => HwmonControlMode.Software,
                    _ => HwmonControlMode.Undefined
                };
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_control_software_value")]
    public static HwmonStatus SensorGetControlSoftwareValue(IntPtr handle, float* outValue, bool* outHasValue, HwmonError* err)
    {
        if (outValue == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control != null)
            {
                *outValue = control.SoftwareValue;
                *outHasValue = true;
            }
            else
            {
                *outValue = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_control_min_software_value")]
    public static HwmonStatus SensorGetControlMinSoftwareValue(IntPtr handle, float* outValue, bool* outHasValue, HwmonError* err)
    {
        if (outValue == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control != null)
            {
                *outValue = control.MinSoftwareValue;
                *outHasValue = true;
            }
            else
            {
                *outValue = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_get_control_max_software_value")]
    public static HwmonStatus SensorGetControlMaxSoftwareValue(IntPtr handle, float* outValue, bool* outHasValue, HwmonError* err)
    {
        if (outValue == null || outHasValue == null) return HwmonStatus.ErrorInvalidArgument;
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control != null)
            {
                *outValue = control.MaxSoftwareValue;
                *outHasValue = true;
            }
            else
            {
                *outValue = 0f;
                *outHasValue = false;
            }
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_set_control_software")]
    public static HwmonStatus SensorSetControlSoftware(IntPtr handle, float value, HwmonError* err)
    {
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control == null)
            {
                SetError(err, HwmonStatus.ErrorNotSupported, $"Sensor '{sensor.Name}' has no control capability");
                return HwmonStatus.ErrorNotSupported;
            }
            var min = control.MinSoftwareValue;
            var max = control.MaxSoftwareValue;
            var target = max > min ? Math.Clamp(value, min, max) : value;
            control.SetSoftware(target);
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }

    [UnmanagedCallersOnly(EntryPoint = "hwmon_sensor_set_control_default")]
    public static HwmonStatus SensorSetControlDefault(IntPtr handle, HwmonError* err)
    {
        try
        {
            var sensor = GetTarget<ISensor>(handle);
            if (sensor == null)
            {
                SetError(err, HwmonStatus.ErrorInvalidArgument, "Invalid Sensor handle");
                return HwmonStatus.ErrorInvalidArgument;
            }
            var control = sensor.Control;
            if (control == null)
            {
                SetError(err, HwmonStatus.ErrorNotSupported, $"Sensor '{sensor.Name}' has no control capability");
                return HwmonStatus.ErrorNotSupported;
            }
            control.SetDefault();
            return HwmonStatus.Ok;
        }
        catch (Exception ex)
        {
            HandleException(err, ex);
            return err != null ? err->Code : HwmonStatus.ErrorInternal;
        }
    }
}
