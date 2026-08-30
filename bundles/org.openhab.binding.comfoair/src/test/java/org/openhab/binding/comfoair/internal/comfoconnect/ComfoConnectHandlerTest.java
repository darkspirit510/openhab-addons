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

import org.jspecify.annotations.NonNull;
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

import com.zehnder.proto.Zehnder.CnRpdoNotification;
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
        protocolHandler.setSensorHandler(testCallback);
    }

    static Stream<Arguments> sensorPayloadsAndStates() {
        return Stream.of(
                // Sensor 210: Season Heating Active (0x00D2) - BOOL type, value ON (0x01)
                argumentsOf(210, new byte[] { 0x01 }, OnOffType.ON),
                // Sensor 211: Season Cooling Active (0x00D3) - BOOL type, value OFF (0x00)
                argumentsOf(211, new byte[] { 0x00 }, OnOffType.OFF),
                // Sensor 221: Supply Air Temperature (0x00DD) - INT16, value -50 (0xFFCE in little-endian)
                argumentsOf(221, new byte[] { (byte) 0xCE, (byte) 0xFF }, new DecimalType(-5.0)),
                // Sensor 274: Extract Air Temperature (0x0112) - INT16, value -25 (0xFFE7 in little-endian)
                argumentsOf(274, new byte[] { (byte) 0xE7, (byte) 0xFF }, new DecimalType(-2.5)),
                // Sensor 275: Exhaust Air Temperature (0x0113) - INT16, value 125 (0x007D in little-endian)
                argumentsOf(275, new byte[] { 0x7D, 0x00 }, new DecimalType(12.5)),
                // Sensor 276: Outdoor Air Temperature (0x0114) - INT16, value 250 (0x00FA in little-endian)
                argumentsOf(276, new byte[] { (byte) 0xFA, 0x00 }, new DecimalType(25.0)),
                // Sensor 290: Extract Air Humidity (0x0122) - UINT8, value 45%
                argumentsOf(290, new byte[] { 45 }, new DecimalType(45)),
                // Sensor 291: Exhaust Air Humidity (0x0123) - UINT8, value 60%
                argumentsOf(291, new byte[] { 60 }, new DecimalType(60)),
                // Sensor 292: Outdoor Air Humidity (0x0124) - UINT8, value 75%
                argumentsOf(292, new byte[] { 75 }, new DecimalType(75)),
                // Sensor 294: Supply Air Humidity (0x0126) - UINT8, value 50%
                argumentsOf(294, new byte[] { 50 }, new DecimalType(50)),
                // Sensor 128: Power Usage (0x0080) - UINT16, value 200W (0x00C8 in little-endian)
                argumentsOf(128, new byte[] { (byte) 0xC8, 0x00 }, new DecimalType(200)),
                // Sensor 129: Power Usage Total Year (0x0081) - UINT16, value 1500 kWh (0x05DC in little-endian)
                argumentsOf(129, new byte[] { (byte) 0xDC, 0x05 }, new DecimalType(1500)),
                // Sensor 130: Power Usage Total (0x0082) - UINT16, value 3000 kWh (0x0BB8 in little-endian)
                argumentsOf(130, new byte[] { (byte) 0xB8, 0x0B }, new DecimalType(3000)),
                // Sensor 144: Preheater Power Total Year (0x0090) - UINT16, value 800 kWh (0x0320 in little-endian)
                argumentsOf(144, new byte[] { 0x20, 0x03 }, new DecimalType(800)),
                // Sensor 145: Preheater Power Total (0x0091) - UINT16, value 1200 kWh (0x04B0 in little-endian)
                argumentsOf(145, new byte[] { (byte) 0xB0, 0x04 }, new DecimalType(1200)),
                // Sensor 146: Preheater Power (0x0092) - UINT16, value 250W (0x00FA in little-endian)
                argumentsOf(146, new byte[] { (byte) 0xFA, 0x00 }, new DecimalType(250)),
                // Sensor 192: Days to Replace Filter (0x00C0) - UINT16, value 90 days (0x005A in little-endian)
                argumentsOf(192, new byte[] { 0x5A, 0x00 }, new DecimalType(90)),
                // Sensor 213: Avoided Heating Power (0x00D5) - UINT16, value 150W (0x0096 in little-endian)
                argumentsOf(213, new byte[] { (byte) 0x96, 0x00 }, new DecimalType(150)),
                // Sensor 214: Avoided Heating Total Year (0x00D6) - UINT16, value 250 kWh (0x00FA in little-endian)
                argumentsOf(214, new byte[] { (byte) 0xFA, 0x00 }, new DecimalType(250)),
                // Sensor 215: Avoided Heating Total (0x00D7) - UINT16, value 500 kWh (0x01F4 in little-endian)
                argumentsOf(215, new byte[] { (byte) 0xF4, 0x01 }, new DecimalType(500)),
                // Sensor 216: Avoided Cooling Power (0x00D8) - UINT16, value 100W (0x0064 in little-endian)
                argumentsOf(216, new byte[] { 0x64, 0x00 }, new DecimalType(100)),
                // Sensor 217: Avoided Cooling Total Year (0x00D9) - UINT16, value 180 kWh (0x00B4 in little-endian)
                argumentsOf(217, new byte[] { (byte) 0xB4, 0x00 }, new DecimalType(180)),
                // Sensor 218: Avoided Cooling Total (0x00DA) - UINT16, value 350 kWh (0x015E in little-endian)
                argumentsOf(218, new byte[] { (byte) 0x5E, 0x01 }, new DecimalType(350)));
    }

    private static @NonNull Arguments argumentsOf(int sensorId, byte[] payload, State state) {
        return Arguments.of(createRpdoNotification(sensorId, payload), sensorId, state);
    }

    private static byte[] createRpdoNotification(int sensorId, byte[] data) {
        return CnRpdoNotification.newBuilder().setPdid(sensorId).setData(com.google.protobuf.ByteString.copyFrom(data))
                .build().toByteArray();
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
        // Create a notification for unknown sensor ID 0x03E7 (1000)
        byte[] rpdoPayload = createRpdoNotification(1000, new byte[] { 0x3C });

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
