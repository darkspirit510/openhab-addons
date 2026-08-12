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
package org.openhab.binding.comfoair.internal.comfoconnect.usecase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.comfoair.internal.comfoconnect.RequestExecutor;

import com.zehnder.proto.Zehnder;

/**
 * Use case for listing registered apps on the ComfoConnect gateway.
 * Handles the protocol request and response parsing logic.
 */
@NonNullByDefault
public class ListRegisteredAppsUseCase {

    private final RequestExecutor requestExecutor;

    /**
     * Create a new use case instance.
     *
     * @param requestExecutor executor that sends protocol requests and returns byte array responses
     */
    public ListRegisteredAppsUseCase(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    /**
     * Execute the use case: list all registered app UUIDs.
     *
     * @return list of registered app UUIDs
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted during execution
     */
    public List<UUID> execute() throws IOException, InterruptedException {
        Zehnder.ListRegisteredAppsRequest.Builder builder = Zehnder.ListRegisteredAppsRequest.newBuilder();
        byte[] response = requestExecutor.execute(builder.build());

        return parseResponse(response);
    }

    /**
     * Parse the protobuf response into a list of UUIDs.
     *
     * @param response the raw response bytes
     * @return list of UUIDs
     * @throws IOException if parsing fails
     */
    private List<UUID> parseResponse(byte[] response) throws IOException {
        List<UUID> uuids = new ArrayList<>();

        try {
            Zehnder.ListRegisteredAppsConfirm confirm = Zehnder.ListRegisteredAppsConfirm.parseFrom(response);

            for (Zehnder.ListRegisteredAppsConfirm.App app : confirm.getAppsList()) {
                UUID registeredUuid = bytesToUuid(app.getUuid().toByteArray());
                uuids.add(registeredUuid);
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse registered apps response", e);
        }

        return uuids;
    }

    /**
     * Convert UUID bytes to UUID object.
     *
     * @param bytes the 16 bytes representing UUID
     * @return UUID object
     */
    private UUID bytesToUuid(final byte[] bytes) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();

        return new UUID(high, low);
    }
}
