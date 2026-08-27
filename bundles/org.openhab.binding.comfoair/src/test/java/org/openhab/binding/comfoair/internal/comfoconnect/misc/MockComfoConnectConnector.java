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
package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import java.io.IOException;
import java.util.UUID;

import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;

/**
 * Mock implementation of ComfoConnectConnector for testing.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class MockComfoConnectConnector extends ComfoConnectConnector {

    public MockComfoConnectConnector() {
        super("test", 1234, UUID.randomUUID(), UUID.randomUUID());
    }

    @Override
    public ProtobufFramer getFramer() {
        return new MockProtobufFramer();
    }

    @Override
    public void sendMessage(byte[] message) throws IOException {
        // Track sent messages
    }

    @Override
    public UUID clientUuid() {
        return UUID.randomUUID();
    }
}
