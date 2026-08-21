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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder;

/**
 * Tests for the DecimalSensor class.
 *
 * @author Sascha Knoop - Initial contribution
 */
class DecimalSensorTest {

    @Test
    @DisplayName("Extract unsigned 8-bit integer from payload")
    void testValueAsStateWithUint8() {
        DecimalSensor sensor = new DecimalSensor(1, SensorValueType.UnsignedByte, "test-channel");
        // Data bytes directly: value 42 (0x2A) at byte 0
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(1)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x2A })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(42), state);
    }

    @Test
    @DisplayName("Extract unsigned 16-bit integer from payload")
    void testValueAsStateWithUint16() {
        DecimalSensor sensor = new DecimalSensor(2, SensorValueType.UnsignedShort, "test-channel");
        // Data bytes: value 276 (0x0114) in little-endian = 0x14 0x01
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(2)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x14, 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(276), state);
    }

    @Test
    @DisplayName("Extract unsigned 32-bit integer from payload")
    void testValueAsStateWithUint32() {
        DecimalSensor sensor = new DecimalSensor(3, SensorValueType.UnsignedInt, "test-channel");
        // Data bytes: value 100 (0x00000064) in little-endian = 0x64 0x00 0x00 0x00
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(3)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x64, 0x00, 0x00, 0x00 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(100), state);
    }

    @Test
    @DisplayName("Extract signed 8-bit integer from payload")
    void testValueAsStateWithInt8() {
        DecimalSensor sensor = new DecimalSensor(4, SensorValueType.SignedByte, "test-channel");
        // Data bytes: value -1 (0xFF as signed byte)
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(4)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { (byte) 0xFF })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(-1.0), state);
    }

    @Test
    @DisplayName("Extract signed 16-bit integer from payload")
    void testValueAsStateWithInt16() {
        DecimalSensor sensor = new DecimalSensor(5, SensorValueType.SignedShort, "test-channel");
        // Data bytes: value -256 (0xFF00 in two's complement) in little-endian = 0x00 0xFF
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(5)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, (byte) 0xFF })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(-256.0), state);
    }

    @Test
    @DisplayName("Extract signed 64-bit integer from payload")
    void testValueAsStateWithInt64() {
        DecimalSensor sensor = new DecimalSensor(6, SensorValueType.SignedLong, "test-channel");
        // Data bytes: value 100 (0x0000000000000064) in little-endian = 0x64 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(6).setData(
                com.google.protobuf.ByteString.copyFrom(new byte[] { 0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(100), state);
    }

    @Test
    @DisplayName("Return 0 when payload is too short")
    void testValueAsStateWithShortPayload() {
        DecimalSensor sensor = new DecimalSensor(7, SensorValueType.UnsignedByte, "test-channel");
        // Empty payload should return 0
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(7)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] {})).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(0), state);
    }

    @Test
    @DisplayName("Apply transformation to extracted value")
    void testValueAsStateWithTransformation() {
        DecimalSensor sensor = new DecimalSensor(8, SensorValueType.UnsignedByte, "test-channel")
                .withTransformation(value -> value / 10.0);
        // Data bytes: value 100 (0x64) at byte 0, transformed to 10
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(8)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x64 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(10), state);
    }

    @Test
    @DisplayName("Use default identity transformation")
    void testValueAsStateWithDefaultTransformation() {
        DecimalSensor sensor = new DecimalSensor(9, SensorValueType.UnsignedByte, "test-channel");
        // Data bytes: value 10 (0x0A) at byte 0
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(9)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x0A })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(10), state);
    }

    @Test
    @DisplayName("Return 0 for unknown sensor type")
    void testValueAsStateWithUnknownSensorType() {
        DecimalSensor sensor = new DecimalSensor(10, SensorValueType.Version, "test-channel");
        // Unknown type should return 0
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(10)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertEquals(new DecimalType(0), state);
    }
}
