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

import java.util.UUID;

public abstract class Response {
    /**
     * Convert 16 bytes to a UUID string.
     *
     * @param bytes the 16-byte UUID
     * @return the UUID as a string
     */
    protected static String bytesToUuid(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes long");
        }

        long most = 0;
        long least = 0;

        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xFF);
            least = (least << 8) | (bytes[8 + i] & 0xFF);
        }

        UUID uuid = new UUID(most, least);

        return uuid.toString();
    }
}
