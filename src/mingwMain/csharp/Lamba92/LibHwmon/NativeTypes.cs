using System.Runtime.InteropServices;

namespace Lamba92.LibHwmon;

public enum HwmonStatus
{
    Ok = 0,
    ErrorInvalidArgument = 1,
    ErrorNotFound = 2,
    ErrorUnauthorized = 3,
    ErrorNotSupported = 4,
    ErrorInternal = 5
}

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
public unsafe struct HwmonError
{
    public HwmonStatus Code;
    public fixed byte Message[1024];
}

[StructLayout(LayoutKind.Sequential)]
public struct HwmonConfig
{
    [MarshalAs(UnmanagedType.I1)] public bool EnableCpu;
    [MarshalAs(UnmanagedType.I1)] public bool EnableGpu;
    [MarshalAs(UnmanagedType.I1)] public bool EnableMotherboard;
    [MarshalAs(UnmanagedType.I1)] public bool EnableMemory;
    [MarshalAs(UnmanagedType.I1)] public bool EnableController;
    [MarshalAs(UnmanagedType.I1)] public bool EnableStorage;
    [MarshalAs(UnmanagedType.I1)] public bool EnableNetwork;
    [MarshalAs(UnmanagedType.I1)] public bool EnableBattery;
}

[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
public unsafe struct HwmonSensorTelemetryRecord
{
    public fixed byte SensorId[256];
    public fixed byte HardwareId[256];
    public HwmonSensorType SensorType;
    public float Value;
    [MarshalAs(UnmanagedType.I1)] public bool HasValue;
    public float Min;
    [MarshalAs(UnmanagedType.I1)] public bool HasMin;
    public float Max;
    [MarshalAs(UnmanagedType.I1)] public bool HasMax;
    public HwmonControlMode ControlMode;
    public float ControlSoftwareValue;
    [MarshalAs(UnmanagedType.I1)] public bool HasControlSoftwareValue;
}

public enum HwmonHardwareType
{
    Unknown = 0,
    Motherboard = 1,
    SuperIO = 2,
    Cpu = 3,
    Ram = 4,
    GpuNvidia = 5,
    GpuAmd = 6,
    GpuIntel = 7,
    Storage = 8,
    Network = 9,
    Controller = 10,
    Battery = 11,
    EmbeddedController = 12
}

public enum HwmonSensorType
{
    Voltage = 0,
    Current = 1,
    Power = 2,
    Clock = 3,
    Temperature = 4,
    Load = 5,
    Frequency = 6,
    Fan = 7,
    Flow = 8,
    Control = 9,
    Level = 10,
    Factor = 11,
    Data = 12,
    Throughput = 13,
    TimeSpan = 14,
    Energy = 15,
    Noise = 16,
    Humidity = 17
}

public enum HwmonControlMode
{
    Undefined = 0,
    Default = 1,
    Software = 2
}
