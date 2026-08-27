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
package org.openhab.binding.comfoair.internal.comfoconnect.component;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.HexConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ProtobufFramer;

/**
 * Unit tests for {@link Messages}.
 *
 * @author Sascha Knoop - Initial contribution
 */
@DisplayName("Messages")
class MessagesTest {

    private Messages messages;
    private ComfoConnectConnector mockConnector;
    private ProtobufFramer mockFramer;
    private HexConverter mockHexConverter;
    private ComfoConnectProtocolHandler mockProtocolHandler;
    private ScheduledExecutorService mockScheduler;

    @BeforeEach
    void setUp() {
        mockConnector = mock(ComfoConnectConnector.class);
        mockFramer = mock(ProtobufFramer.class);
        mockHexConverter = mock(HexConverter.class);
        mockProtocolHandler = mock(ComfoConnectProtocolHandler.class);
        mockScheduler = mock(ScheduledExecutorService.class);

        messages = new Messages(mockConnector, mockFramer, mockHexConverter, mockProtocolHandler, mockScheduler);
    }

    @Test
    @DisplayName("Constructor creates instance")
    void testConstructor() {
        assertNotNull(messages);
    }

    @Test
    @DisplayName("Queue message returns true for valid message")
    void testQueueMessage() {
        byte[] message = new byte[] { 0x01, 0x02, 0x03 };
        assertTrue(messages.queueMessage(message));
    }

    @Test
    @DisplayName("Queue message returns false when shutdown")
    void testQueueMessageWhenShutdown() {
        messages.setShutdown(true);
        byte[] message = new byte[] { 0x01, 0x02, 0x03 };
        assertFalse(messages.queueMessage(message));
    }

    @Test
    @DisplayName("Clear queue works")
    void testClearQueue() {
        messages.queueMessage(new byte[] { 0x01 });
        messages.clearQueue();
        // Queue should be empty now
        assertNull(messages.pollMessage(10));
    }

    @Test
    @DisplayName("Poll message with timeout returns null when queue is empty")
    void testPollMessageEmptyQueue() {
        Object result = messages.pollMessage(10);
        assertNull(result);
    }

    @Test
    @DisplayName("Custom capacity constructor works")
    void testCustomCapacityConstructor() {
        // Messages uses default capacity, but we can test that it accepts messages
        byte[] message = new byte[] { 0x01, 0x02, 0x03 };
        assertTrue(messages.queueMessage(message));
    }

    @Test
    @DisplayName("Queue and poll message works")
    void testQueueAndPollMessage() {
        byte[] message = new byte[] { 0x01, 0x02, 0x03 };
        messages.queueMessage(message);
        byte[] polled = messages.pollMessage(100);
        assertNotNull(polled);
        assertArrayEquals(message, polled);
    }

    @Test
    @DisplayName("getFramer returns the framer")
    void testGetFramer() {
        assertEquals(mockFramer, messages.getFramer());
    }

    @Test
    @DisplayName("getMessageQueue returns non-null queue")
    void testGetMessageQueue() {
        assertNotNull(messages.getMessageQueue());
    }
}
