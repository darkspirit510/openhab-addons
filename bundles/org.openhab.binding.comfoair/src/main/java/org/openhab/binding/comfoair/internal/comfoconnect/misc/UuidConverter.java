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

import java.nio.ByteBuffer;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.protobuf.ByteString;

/**
 * Converts between UUID and byte representations.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class UuidConverter {

    /**
     * Convert UUID to 16-byte array.
     *
     * @param uuid the UUID to convert
     * @return 16-byte array representation
     */
    public byte[] toBytes(final UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bytes;
    }

    /**
     * Convert 16-byte array to UUID.
     *
     * @param bytes the 16-byte array to convert
     * @return UUID representation
     * @throws IllegalArgumentException if bytes is not 16 bytes long
     */
    public UUID fromBytes(final byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes long");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Convert ByteString to UUID.
     *
     * @param byteString the ByteString to convert
     * @return UUID representation
     * @throws IllegalArgumentException if byteString is null or not 16 bytes
     */
    public UUID fromByteString(final ByteString byteString) {
        return fromBytes(byteString.toByteArray());
    }
}
