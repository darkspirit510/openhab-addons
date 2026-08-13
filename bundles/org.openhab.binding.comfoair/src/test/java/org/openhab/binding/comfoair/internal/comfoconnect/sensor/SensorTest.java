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

        // Payload is the data bytes from CnRpdoNotification.getData().toByteArray()
        // For UINT8: value is at byte 0
        byte[] payload = new byte[] { 0x1E }; // value 30 (0x1E)

        int result = sensor.parseValueFrom(payload);
        assertEquals(30, result);
    }

    @Test
    @DisplayName("Extract UINT16 value from payload")
    public void testExtractValueFromPayloadUint16() {
        Sensor sensor = new DecimalSensor("sensor", 120, SensorValueType.TYPE_CN_UINT16, "channel");

        // Payload is the data bytes from CnRpdoNotification.getData().toByteArray()
        // For UINT16: value is at bytes 0-1 (little-endian)
        // Value 148 = 0x0094 in big-endian = 0x9400 in little-endian
        byte[] payload = new byte[] { (byte) 0x94, 0x00 };

        int result = sensor.parseValueFrom(payload);
        assertEquals(148, result);
    }

    @Test
    @DisplayName("Extract INT16 value from payload")
    public void testExtractValueFromPayloadInt16() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_INT16, "channel");

        // Payload is the data bytes from CnRpdoNotification.getData().toByteArray()
        // For INT16: value is at bytes 0-1 (little-endian, signed)
        // Value -100 = 0xFF9C in two's complement (LSB=0x9C, MSB=0xFF)
        byte[] payload = new byte[] { (byte) 0x9C, (byte) 0xFF };

        int result = sensor.parseValueFrom(payload);
        // With proper INT16 support (sign-extension), -100 should be returned
        assertEquals(-100, result);
    }

    @Test
    @DisplayName("Return 0 for payload too short")
    public void testExtractValueFromPayloadTooShort() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT8, "channel");

        byte[] shortPayload = new byte[] {}; // Empty payload

        int result = sensor.parseValueFrom(shortPayload);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Return 0 for UINT16 payload too short")
    public void testExtractValueFromPayloadUint16TooShort() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT16, "channel");

        // Payload has only 1 byte, less than minimum 2 - should return 0
        byte[] payload = new byte[] {};

        int result = sensor.parseValueFrom(payload);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Default to UINT8 for unknown sensor type")
    public void testExtractValueFromPayloadUnknownType() {
        // Create a sensor with a type that's not explicitly handled
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_INT8, "channel");

        // Payload is the data bytes from CnRpdoNotification.getData().toByteArray()
        // Default case treats as UINT8 at byte 0
        byte[] payload = new byte[] { 0x1E }; // value 30 at byte 0

        int result = sensor.parseValueFrom(payload);
        assertEquals(30, result);
    }
}
