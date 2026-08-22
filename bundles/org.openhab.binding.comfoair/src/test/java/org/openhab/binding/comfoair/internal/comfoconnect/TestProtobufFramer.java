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

import org.openhab.binding.comfoair.internal.comfoconnect.misc.ParsedFrame;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ProtobufFramer;

import com.zehnder.proto.Zehnder.GatewayOperation;

/**
 * Test framer that returns predefined parsed frames for testing.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class TestProtobufFramer extends ProtobufFramer {

    private final byte[] command;
    private final byte[] payload;

    public TestProtobufFramer(byte[] command, byte[] payload) {
        super(UUID.randomUUID(), UUID.randomUUID());
        this.command = command;
        this.payload = payload;
    }

    @Override
    public ParsedFrame parseFrame(byte[] frame) {
        try {
            GatewayOperation operation = GatewayOperation.parseFrom(command);
            return new ParsedFrame(UUID.randomUUID(), UUID.randomUUID(), operation.toByteArray(), payload);
        } catch (Exception e) {
            return null;
        }
    }
}
