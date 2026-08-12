/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Sensor} class.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorTest {

    @Test
    @DisplayName("toString returns formatted sensor info")
    public void testToString() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT8, "");

        assertEquals("sensor (42)", sensor.toString());
    }

    @Test
    @DisplayName("Extract UINT8 value from payload")
    public void testExtractValueFromPayloadUint8() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT8, "channel");

        // Payload structure: [fieldTag, sensorId, fieldTag, value]
        byte[] payload = new byte[] { 0x08, 0x2A, 0x12, 0x1E }; // sensor ID 42, value 30 (0x1E)

        int result = sensor.parseValueFrom(payload);
        assertEquals(30, result);
    }

    @Test
    @DisplayName("Extract UINT16 value from payload")
    public void testExtractValueFromPayloadUint16() {
        Sensor sensor = new DecimalSensor("sensor", 120, SensorValueType.TYPE_CN_UINT16, "channel");

        // Payload structure: [fieldTag, sensorIdLow, sensorIdHigh, fieldTag, length, valueLSB, valueMSB]
        // For value 148 (0x0094 in big-endian, 0x9400 in little-endian)
        // Little-endian: LSB=0x94, MSB=0x00
        // Sensor ID 120 (0x0078): low=0x78, high=0x00
        byte[] payload = new byte[] { 0x08, 0x78, 0x00, 0x12, 0x02, (byte) 0x94, 0x00 };

        int result = sensor.parseValueFrom(payload);
        assertEquals(148, result);
    }

    @Test
    @DisplayName("Extract INT16 value from payload")
    public void testExtractValueFromPayloadInt16() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_INT16, "channel");

        // For INT16, same extraction as UINT16 but interpreted as signed
        // Value -100 in little-endian: LSB=0x9C, MSB=0xFF (0xFF9C = -100 in two's complement)
        // Sensor ID 42 (0x002A): low=0x2A, high=0x00
        byte[] payload = new byte[] { 0x08, 0x2A, 0x00, 0x12, 0x02, (byte) 0x9C, (byte) 0xFF };

        int result = sensor.parseValueFrom(payload);
        // With proper INT16 support (sign-extension), -100 should be returned
        assertEquals(-100, result);
    }

    @Test
    @DisplayName("Return 0 for payload too short")
    public void testExtractValueFromPayloadTooShort() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT8, "channel");

        byte[] shortPayload = new byte[] { 0x08, 0x2A }; // Only 2 bytes

        int result = sensor.parseValueFrom(shortPayload);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Return 0 for UINT16 payload too short")
    public void testExtractValueFromPayloadUint16TooShort() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT16, "channel");

        // Payload has only 3 bytes, less than minimum 4
        byte[] payload = new byte[] { 0x08, 0x2A, 0x12 };

        int result = sensor.parseValueFrom(payload);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Default to UINT8 for unknown sensor type")
    public void testExtractValueFromPayloadUnknownType() {
        // Create a sensor with a type that's not explicitly handled
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_INT8, "channel");

        byte[] payload = new byte[] { 0x08, 0x2A, 0x12, 0x1E }; // value 30 at byte 3

        int result = sensor.parseValueFrom(payload);
        assertEquals(30, result);
    }
}
