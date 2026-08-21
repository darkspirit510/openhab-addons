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

import java.util.UUID;

/**
 * Test connector that provides controlled responses for testing.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class TestComfoConnectConnector extends ComfoConnectConnector {

    private byte[] nextCommand = new byte[0];
    private byte[] nextPayload = new byte[0];

    public TestComfoConnectConnector() {
        super("test-host", 1234, UUID.randomUUID(), UUID.randomUUID());
    }

    public void setNextParsedFrame(byte[] command, byte[] payload) {
        this.nextCommand = command != null ? command : new byte[0];
        this.nextPayload = payload != null ? payload : new byte[0];
    }

    @Override
    public ProtobufFramer getFramer() {
        return new TestProtobufFramer(nextCommand, nextPayload);
    }

    @Override
    public UUID getClientUuid() {
        return UUID.randomUUID();
    }
}
