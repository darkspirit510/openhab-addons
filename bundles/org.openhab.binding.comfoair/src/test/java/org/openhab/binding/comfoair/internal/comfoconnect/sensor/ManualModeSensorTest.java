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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openhab.core.library.types.StringType;

import com.google.protobuf.ByteString;
import com.zehnder.proto.Zehnder.CnRpdoNotification;

/**
 * Unit tests for {@link ManualModeSensor} class.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
class ManualModeSensorTest {

    private final ManualModeSensor sensor = new ManualModeSensor(56, SensorValueType.SignedByte, "test-channel");

    @ParameterizedTest
    @MethodSource("manualModeValues")
    @DisplayName("Returns expected manual mode for given input")
    void testReturnsExpectedModeForGivenInput(byte[] payload, String expectedMode) {
        assertEquals(new StringType(expectedMode), sensor.valueAsState(messageFor(payload)));
    }

    private static CnRpdoNotification messageFor(byte[] payload) {
        return CnRpdoNotification.newBuilder().setPdid(56).setData(ByteString.copyFrom(payload)).build();
    }

    private static Stream<Arguments> manualModeValues() {
        return Stream.of(Arguments.of(new byte[] { (byte) 0xFF }, "auto"),
                Arguments.of(new byte[] { 0x01 }, "unlimited_manual"));
    }

    @Test
    @DisplayName("Returns null for unknown manual mode value")
    void testReturnsNullForUnknownMode() {
        assertNull(sensor.valueAsState(messageFor(new byte[] { 0x00 })));
    }

    @Test
    @DisplayName("Returns null for empty payload")
    void testReturnsNullForEmptyPayload() {
        assertNull(sensor.valueAsState(messageFor(new byte[] {})));
    }
}
