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
        Sensor sensor = new DecimalSensor(42, SensorValueType.UnsignedByte, "channel-id");

        assertEquals("channel-id (42)", sensor.toString());
    }

    @Test
    @DisplayName("extractUnsignedByte extracts correct value")
    public void testExtractUnsignedByte() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.UnsignedByte, "");
        byte[] payload = new byte[] { (byte) 0xFF, 0x00, 0x01 };

        double result = sensor.extractUnsignedByte(payload);
        assertEquals(255.0, result, 0.001);
    }

    @Test
    @DisplayName("extractUnsignedByte returns 0 for empty payload")
    public void testExtractUnsignedByteEmpty() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.UnsignedByte, "");
        byte[] payload = new byte[0];

        double result = sensor.extractUnsignedByte(payload);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    @DisplayName("extractUnsignedShort extracts correct value")
    public void testExtractUnsignedShort() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.UnsignedShort, "");
        byte[] payload = new byte[] { (byte) 0xFF, (byte) 0xFF };

        double result = sensor.extractUnsignedShort(payload);
        assertEquals(65535.0, result, 0.001);
    }

    @Test
    @DisplayName("extractUnsignedInt extracts correct value")
    public void testExtractUnsignedInt() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.UnsignedInt, "");
        byte[] payload = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };

        double result = sensor.extractUnsignedInt(payload);
        assertEquals(4294967295.0, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedByte extracts correct positive value")
    public void testExtractSignedBytePositive() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedByte, "");
        byte[] payload = new byte[] { (byte) 0x7F };

        double result = sensor.extractSignedByte(payload);
        assertEquals(127.0, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedByte extracts correct negative value")
    public void testExtractSignedByteNegative() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedByte, "");
        byte[] payload = new byte[] { (byte) 0xFF };

        double result = sensor.extractSignedByte(payload);
        assertEquals(-1.0, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedShort extracts correct positive value")
    public void testExtractSignedShortPositive() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedShort, "");
        byte[] payload = new byte[] { 0x00, (byte) 0x7F };

        double result = sensor.extractSignedShort(payload);
        assertEquals(32512.0, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedShort extracts correct negative value")
    public void testExtractSignedShortNegative() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedShort, "");
        byte[] payload = new byte[] { 0x00, (byte) 0xFF };

        double result = sensor.extractSignedShort(payload);
        assertEquals(-256.0, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedLong extracts correct positive value")
    public void testExtractSignedLongPositive() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedLong, "");
        byte[] payload = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x7F };

        double result = sensor.extractSignedLong(payload);
        assertEquals(9.151314442816848E18, result, 0.001);
    }

    @Test
    @DisplayName("extractSignedLong extracts correct negative value")
    public void testExtractSignedLongNegative() {
        Sensor sensor = new DecimalSensor(42, SensorValueType.SignedLong, "");
        byte[] payload = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF };

        double result = sensor.extractSignedLong(payload);
        assertEquals(-1.0, result, 0.001);
    }
}
