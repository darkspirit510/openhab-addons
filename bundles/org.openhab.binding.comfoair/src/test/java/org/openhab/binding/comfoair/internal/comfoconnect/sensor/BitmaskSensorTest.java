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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder;

/**
 * Tests for the BitmaskSensor class.
 *
 * @author Sascha Knoop - Initial contribution
 */
class BitmaskSensorTest {

    @Test
    @DisplayName("Return DecimalType with bitmask value")
    void testValueAsStateReturnsBitmask() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 });
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Create a bitmask with bit 45 set (validation bit) and bit 9 set (frostProtection)
        long bitmask = (1L << 45) | (1L << 9);
        byte[] payload = longToBytes(bitmask);

        Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(230)
                .setData(com.google.protobuf.ByteString.copyFrom(payload)).build();

        State state = sensor.valueAsState(message);
        assertNotNull(state);
        assertTrue(state instanceof DecimalType);
        assertEquals(bitmask, ((DecimalType) state).longValue());
    }

    @Test
    @DisplayName("Process bitmask with validation bit not set - all channels OFF")
    void testProcessBitmaskValidationBitNotSet() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 });
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Simulate linked channels by directly setting the linkedChannels set
        // This is a test-only approach to verify the logic without complex mocking
        Set<String> linkedChannels = new HashSet<>();
        linkedChannels.add("resistance");
        linkedChannels.add("frostProtection");

        // Use reflection to set the linkedChannels field for testing
        try {
            java.lang.reflect.Field field = BitmaskSensor.class.getDeclaredField("linkedChannels");
            field.setAccessible(true);
            field.set(sensor, linkedChannels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set linkedChannels for testing", e);
        }

        // Create a bitmask WITHOUT bit 45 set (validation bit not set)
        long bitmask = (1L << 9); // Only frostProtection bit set
        Map<String, State> channelStates = sensor.processBitmaskUpdate(bitmask);

        // All channels should be OFF when validation bit is not set
        assertEquals(OnOffType.OFF, channelStates.get("resistance"));
        assertEquals(OnOffType.OFF, channelStates.get("frostProtection"));
    }

    @Test
    @DisplayName("Process bitmask with single bit constraint active")
    void testProcessBitmaskSingleBitActive() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 });
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Simulate linked channels
        Set<String> linkedChannels = new HashSet<>();
        linkedChannels.add("resistance");
        linkedChannels.add("frostProtection");

        try {
            java.lang.reflect.Field field = BitmaskSensor.class.getDeclaredField("linkedChannels");
            field.setAccessible(true);
            field.set(sensor, linkedChannels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set linkedChannels for testing", e);
        }

        // Create a bitmask with bit 45 set (validation bit) and bit 9 set (frostProtection)
        long bitmask = (1L << 45) | (1L << 9);
        Map<String, State> channelStates = sensor.processBitmaskUpdate(bitmask);

        // frostProtection should be ON, resistance should be OFF
        assertEquals(OnOffType.ON, channelStates.get("frostProtection"));
        assertEquals(OnOffType.OFF, channelStates.get("resistance"));
    }

    @Test
    @DisplayName("Process bitmask with multi-bit constraint active")
    void testProcessBitmaskMultiBitActive() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 }); // OR logic - ON if either bit 2 or 3 is set
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Simulate linked channels
        Set<String> linkedChannels = new HashSet<>();
        linkedChannels.add("resistance");
        linkedChannels.add("frostProtection");

        try {
            java.lang.reflect.Field field = BitmaskSensor.class.getDeclaredField("linkedChannels");
            field.setAccessible(true);
            field.set(sensor, linkedChannels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set linkedChannels for testing", e);
        }

        // Create a bitmask with bit 45 set (validation bit) and bit 2 set (resistance bit 1)
        long bitmask = (1L << 45) | (1L << 2);
        Map<String, State> channelStates = sensor.processBitmaskUpdate(bitmask);

        // resistance should be ON (bit 2 is set), frostProtection should be OFF
        assertEquals(OnOffType.ON, channelStates.get("resistance"));
        assertEquals(OnOffType.OFF, channelStates.get("frostProtection"));

        // Test with bit 3 set instead
        bitmask = (1L << 45) | (1L << 3);
        channelStates = sensor.processBitmaskUpdate(bitmask);

        // resistance should still be ON (bit 3 is set)
        assertEquals(OnOffType.ON, channelStates.get("resistance"));
        assertEquals(OnOffType.OFF, channelStates.get("frostProtection"));
    }

    @Test
    @DisplayName("Process bitmask with all constraints active")
    void testProcessBitmaskAllActive() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 });
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Simulate linked channels
        Set<String> linkedChannels = new HashSet<>();
        linkedChannels.add("resistance");
        linkedChannels.add("frostProtection");

        try {
            java.lang.reflect.Field field = BitmaskSensor.class.getDeclaredField("linkedChannels");
            field.setAccessible(true);
            field.set(sensor, linkedChannels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set linkedChannels for testing", e);
        }

        // Create a bitmask with bit 45 set (validation bit) and all constraint bits set
        long bitmask = (1L << 45) | (1L << 2) | (1L << 3) | (1L << 9);
        Map<String, State> channelStates = sensor.processBitmaskUpdate(bitmask);

        // All constraints should be ON
        assertEquals(OnOffType.ON, channelStates.get("resistance"));
        assertEquals(OnOffType.ON, channelStates.get("frostProtection"));
    }

    @Test
    @DisplayName("Test isAnyBitSet method")
    void testIsAnyBitSet() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        long bitmask = (1L << 2) | (1L << 5);

        // Test single bit
        assertTrue(sensor.isAnyBitSet(bitmask, new int[] { 2 }));
        assertTrue(sensor.isAnyBitSet(bitmask, new int[] { 5 }));
        assertFalse(sensor.isAnyBitSet(bitmask, new int[] { 3 }));

        // Test multi-bit (OR logic)
        assertTrue(sensor.isAnyBitSet(bitmask, new int[] { 2, 3 })); // bit 2 is set
        assertTrue(sensor.isAnyBitSet(bitmask, new int[] { 5, 6 })); // bit 5 is set
        assertFalse(sensor.isAnyBitSet(bitmask, new int[] { 3, 4 })); // neither bit is set
    }

    @Test
    @DisplayName("Test getBitsForChannel method")
    void testGetBitsForChannel() {
        Map<String, int[]> channelToBitsMap = new HashMap<>();
        channelToBitsMap.put("resistance", new int[] { 2, 3 });
        channelToBitsMap.put("frostProtection", new int[] { 9 });

        BitmaskSensor sensor = new BitmaskSensor(230, SensorValueType.SignedLong, "airflowConstraints",
                channelToBitsMap);

        // Test getting bits for existing channels
        assertTrue(Arrays.equals(new int[] { 2, 3 }, sensor.getBitsForChannel("resistance")));
        assertTrue(Arrays.equals(new int[] { 9 }, sensor.getBitsForChannel("frostProtection")));

        // Test getting bits for non-existent channel
        assertEquals(null, sensor.getBitsForChannel("nonexistent"));
    }

    /**
     * Helper method to convert a long to byte array (little-endian).
     */
    private byte[] longToBytes(long value) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (value >> (i * 8));
        }
        return bytes;
    }
}
