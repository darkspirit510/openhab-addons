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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder;

/**
 * Tests for the BooleanSensor class.
 *
 * @author Sascha Knoop - Initial contribution
 */
class BooleanSensorTest {

    @Test
    @DisplayName("Return ON state when boolean value is non-zero")
    void testValueAsStateWithNonZeroValue() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 1, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(1)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x01, 0x00, 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(OnOffType.ON, state);
    }

    @Test
    @DisplayName("Return OFF state when boolean value is zero")
    void testValueAsStateWithZeroValue() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 2, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(2)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x02, 0x00, 0x00 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(OnOffType.OFF, state);
    }

    @Test
    @DisplayName("Return null when payload is too short")
    void testValueAsStateWithShortPayload() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 3, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(3)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x03, 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertNull(state);
    }

    @Test
    @DisplayName("Return ON state when boolean value is 1")
    void testValueAsStateWithValueOne() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 4, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(4)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x04, 0x00, 0x01 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(OnOffType.ON, state);
    }

    @Test
    @DisplayName("Return ON state when boolean value is 255 (max byte value)")
    void testValueAsStateWithMaxByteValue() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 5, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(5)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x05, 0x00, (byte) 0xFF }))
                .build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(OnOffType.ON, state);
    }

    @Test
    @DisplayName("Return OFF state when boolean value is 0")
    void testValueAsStateWithValueZero() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 6, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(6)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x06, 0x00, 0x00 })).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertEquals(OnOffType.OFF, state);
    }

    @Test
    @DisplayName("Return null when payload has exactly 4 bytes (minimum required is 5)")
    void testValueAsStateWithExactly4Bytes() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 7, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(7)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x07, 0x00 })).build();

        State state = sensor.valueAsState(message);
        assertNull(state);
    }

    @Test
    @DisplayName("Return null when payload is empty")
    void testValueAsStateWithEmptyPayload() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 8, SensorValueType.TYPE_CN_BOOL, "test-channel");
        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(8)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] {})).build();

        State state = sensor.valueAsState(message);
        assertNull(state);
    }

    @Test
    @DisplayName("Return ON state when boolean value is any non-zero value")
    void testValueAsStateWithVariousNonZeroValues() {
        BooleanSensor sensor = new BooleanSensor("Test Sensor", 9, SensorValueType.TYPE_CN_BOOL, "test-channel");

        // Test with value 1
        Zehnder.CnRpdoNotification message1 = Zehnder.CnRpdoNotification.newBuilder().setPdid(9)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x09, 0x00, 0x01 })).build();
        State state1 = sensor.valueAsState(message1);
        assertEquals(OnOffType.ON, state1);

        // Test with value 10
        Zehnder.CnRpdoNotification message2 = Zehnder.CnRpdoNotification.newBuilder().setPdid(10)
                .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { 0x00, 0x00, 0x0A, 0x00, 0x0A })).build();
        State state2 = sensor.valueAsState(message2);
        assertEquals(OnOffType.ON, state2);
    }
}
