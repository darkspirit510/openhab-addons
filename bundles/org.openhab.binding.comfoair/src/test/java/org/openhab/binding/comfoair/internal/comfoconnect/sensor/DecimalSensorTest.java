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
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 1, SensorValueType.TYPE_CN_UINT8, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(1)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x2A })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(42), state);
    }

    @Test
    @DisplayName("Extract unsigned 16-bit integer from payload")
    void testValueAsStateWithUint16() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 2, SensorValueType.TYPE_CN_UINT16, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(2).setData(
                com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x14, 0x01 }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(276), state);
    }

    @Test
    @DisplayName("Extract unsigned 32-bit integer from payload")
    void testValueAsStateWithUint32() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 3, SensorValueType.TYPE_CN_UINT32, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(3).setData(
                com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x64 }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(1677721600L), state);
    }

    @Test
    @DisplayName("Extract signed 8-bit integer from payload")
    void testValueAsStateWithInt8() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 4, SensorValueType.TYPE_CN_INT8, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(4)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, (byte) 0xFF })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(-1.0), state);
    }

    @Test
    @DisplayName("Extract signed 16-bit integer from payload")
    void testValueAsStateWithInt16() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 5, SensorValueType.TYPE_CN_INT16, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(5)
                .setData(com.google.protobuf.ByteString
                        .copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00 }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(-256.0), state);
    }

    @Test
    @DisplayName("Extract signed 64-bit integer from payload")
    void testValueAsStateWithInt64() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 6, SensorValueType.TYPE_CN_INT64, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(6)
                .setData(com.google.protobuf.ByteString
                        .copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x64 }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(7205759403792794000L), state);
    }

    @Test
    @DisplayName("Return 0 when payload is too short")
    void testValueAsStateWithShortPayload() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 7, SensorValueType.TYPE_CN_UINT8, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(7)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x01, 0x02 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(0), state);
    }

    @Test
    @DisplayName("Apply transformation to extracted value")
    void testValueAsStateWithTransformation() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 8, SensorValueType.TYPE_CN_UINT8, "test-channel")
                .withTransformation(value -> value / 10.0);
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(8)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x64 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(10), state);
    }

    @Test
    @DisplayName("Use default identity transformation")
    void testValueAsStateWithDefaultTransformation() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 9, SensorValueType.TYPE_CN_UINT8, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(9)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x0A })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(new DecimalType(10), state);
    }

    @Test
    @DisplayName("Return 0 for unknown sensor type")
    void testValueAsStateWithUnknownSensorType() {
        DecimalSensor sensor = new DecimalSensor("Test Sensor", 10, SensorValueType.TYPE_CN_VERSION, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(10)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x00, 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertEquals(new DecimalType(0), state);
    }
}
