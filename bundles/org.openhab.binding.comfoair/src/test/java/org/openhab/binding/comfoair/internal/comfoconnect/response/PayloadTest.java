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
package org.openhab.binding.comfoair.internal.comfoconnect.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Payload class.
 *
 * @author Sascha Knoop - Initial contribution
 */
class PayloadTest {

    @Test
    @DisplayName("Handles payload creation with valid byte array")
    void testPayloadCreation() {
        byte[] testData = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        Payload payload = new Payload(testData);

        assertNotNull(payload);
        assertEquals(4, payload.length());
        assertNotNull(payload.content);
        assertEquals(testData, payload.content);
    }

    @Test
    @DisplayName("Handles payload length with empty array")
    void testPayloadLengthWithEmptyArray() {
        Payload payload = new Payload(new byte[0]);

        assertEquals(0, payload.length());
    }

    @Test
    @DisplayName("Handles sensor ID with valid values")
    void testSensorId() {
        Payload payload = new Payload(new byte[] { 0x00, 0x14, 0x01, 0x00 });

        assertEquals(276, payload.sensorId());
    }

    @Test
    @DisplayName("Handles sensor ID with zero values")
    void testSensorIdWithZeroValues() {
        Payload payload = new Payload(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        assertEquals(0, payload.sensorId());
    }

    @Test
    @DisplayName("Handles sensor ID with maximum values")
    void testSensorIdWithMaxValues() {
        Payload payload = new Payload(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });

        assertEquals(65535, payload.sensorId());
    }
}
