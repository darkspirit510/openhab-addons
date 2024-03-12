package org.openhab.binding.comfoair.internal.comfoconnect;

public enum SensorValueType {
    TYPE_CN_BOOL(0x00),
    TYPE_CN_UINT8(0x01),
    TYPE_CN_UINT16(0x02),
    TYPE_CN_UINT32(0x03),
    TYPE_CN_INT8(0x05),
    TYPE_CN_INT16(0x06),
    TYPE_CN_INT64(0x08),
    TYPE_CN_STRING(0x09),
    TYPE_CN_TIME(0x10),
    TYPE_CN_VERSION(0x11);

    final int value;

    SensorValueType(int value) {
        this.value = value;
    }
}
