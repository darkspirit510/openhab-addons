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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;

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

    @Test
    @DisplayName("Test complete message flow: handleIncomingMessage -> handleNotification -> handleRpdoNotification -> callback")
    public void testCompleteMessageFlow() throws Exception {
        // Use sensor ID 66 (Supply Fan Speed Percentage) which exists in the known sensors
        byte[] rpdoPayload = new byte[] { 0x08, 0x42, 0x12, 0x19 };

        GatewayOperation operation = GatewayOperation.newBuilder()
                .setType(GatewayOperation.OperationType.CnRpdoNotificationType).setReference(1)
                .setResult(GatewayOperation.GatewayResult.OK).build();

        testConnector.setNextParsedFrame(operation.toByteArray(), rpdoPayload);

        byte[] testFrame = "test_frame".getBytes();
        protocolHandler.handleIncomingMessage(testFrame);

        assertTrue(testCallback.wasCalled());
        assertEquals(25, testCallback.getValue());

        Sensor receivedSensor = testCallback.getSensor();
        assertNotNull(receivedSensor);
        assertEquals(66, receivedSensor.id);
    }

    @Test
    @DisplayName("Test message flow with fan speed sensor (ID 65)")
    public void testFanSpeedMessageFlow() throws Exception {
        byte[] rpdoPayload = new byte[] { 0x08, 0x41, 0x12, 0x02 };

        GatewayOperation operation = GatewayOperation.newBuilder()
                .setType(GatewayOperation.OperationType.CnRpdoNotificationType).setReference(1)
                .setResult(GatewayOperation.GatewayResult.OK).build();

        testConnector.setNextParsedFrame(operation.toByteArray(), rpdoPayload);

        byte[] testFrame = "test_frame".getBytes();
        protocolHandler.handleIncomingMessage(testFrame);

        assertTrue(testCallback.wasCalled());
        assertEquals(2, testCallback.getValue());

        Sensor receivedSensor = testCallback.getSensor();
        assertNotNull(receivedSensor);
        assertEquals(65, receivedSensor.id);
    }

    @Test
    @DisplayName("Test message flow with unknown sensor ID")
    public void testUnknownSensorMessageFlow() throws Exception {
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
    @DisplayName("Test message flow with null frame")
    public void testNullFrame() {
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
