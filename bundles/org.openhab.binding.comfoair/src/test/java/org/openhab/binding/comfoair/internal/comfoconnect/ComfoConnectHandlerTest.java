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
package org.openhab.binding.comfoair.internal.comfoconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder.GatewayOperation;

/**
 * @author Sascha Knoop - Initial contribution
 */
public class ComfoConnectHandlerTest {

    private ComfoConnectProtocolHandler protocolHandler;
    private TestSensorCallback testCallback;
    private TestComfoConnectConnector testConnector;

    @BeforeEach
    public void setUp() {
        testConnector = new TestComfoConnectConnector();
        TestScheduler scheduler = new TestScheduler();
        protocolHandler = new ComfoConnectProtocolHandler(testConnector, 0, false, scheduler);
        testCallback = new TestSensorCallback();
        protocolHandler.setSensorCallback(testCallback);
    }

    static Stream<Arguments> sensorPayloadsAndStates() {
        return Stream.of(
                // Sensor 119: Exhaust Fan Flow
                Arguments.of(new byte[] { 0x08, 0x77, 0x12, 0x02, (byte) 0x8E, 0x00 }, 119, new DecimalType(142)),
                // Sensor 210: Season Heating Active (true)
                Arguments.of(new byte[] { 0x08, (byte) 0xD2, 0x12, 0x01 }, 210, OnOffType.ON),
                // Sensor 211: Season Cooling Active (false)
                Arguments.of(new byte[] { 0x08, (byte) 0xD3, 0x12, 0x00 }, 211, OnOffType.OFF));
    }

    @ParameterizedTest
    @MethodSource("sensorPayloadsAndStates")
    @DisplayName("Handles sensor updates")
    public void testHandlesSensorUpdates(byte[] rpdoPayload, int expectedSensorId, State expectedState) {
        GatewayOperation operation = GatewayOperation.newBuilder()
                .setType(GatewayOperation.OperationType.CnRpdoNotificationType).setReference(1)
                .setResult(GatewayOperation.GatewayResult.OK).build();

        testConnector.setNextParsedFrame(operation.toByteArray(), rpdoPayload);

        byte[] testFrame = "test_frame".getBytes();
        protocolHandler.handleIncomingMessage(testFrame);

        assertTrue(testCallback.wasCalled());

        Sensor receivedSensor = testCallback.getSensor();

        assertNotNull(receivedSensor);
        assertEquals(expectedSensorId, receivedSensor.id);
        assertEquals(expectedState, receivedSensor.valueAsState(testCallback.getMessage()));
    }

    @Test
    @DisplayName("Ignores unknown sensor updates")
    public void testIgnoresUnknownSensorUpdate() {
        byte[] rpdoPayload = new byte[] { 0x08, (byte) 0xE7, 0x03, 0x12, 0x3C };

        GatewayOperation operation = GatewayOperation.newBuilder()
                .setType(GatewayOperation.OperationType.CnRpdoNotificationType).setReference(1)
                .setResult(GatewayOperation.GatewayResult.OK).build();

        testConnector.setNextParsedFrame(operation.toByteArray(), rpdoPayload);

        byte[] testFrame = "test_frame".getBytes();
        protocolHandler.handleIncomingMessage(testFrame);

        assertFalse(testCallback.wasCalled());
    }

    @Test
    @DisplayName("Ignores null message")
    public void testIgnoresNullMessage() {
        protocolHandler.handleIncomingMessage(null);

        assertFalse(testCallback.wasCalled());
    }

    @Test
    @DisplayName("Test message flow with non-RPDO notification")
    public void testNonRpdoNotification() {
        GatewayOperation operation = GatewayOperation.newBuilder().setType(GatewayOperation.OperationType.KeepAliveType)
                .setReference(1).setResult(GatewayOperation.GatewayResult.OK).build();

        testConnector.setNextParsedFrame(operation.toByteArray(), new byte[0]);

        byte[] testFrame = "test_frame".getBytes();
        protocolHandler.handleIncomingMessage(testFrame);

        assertFalse(testCallback.wasCalled());
    }
}
