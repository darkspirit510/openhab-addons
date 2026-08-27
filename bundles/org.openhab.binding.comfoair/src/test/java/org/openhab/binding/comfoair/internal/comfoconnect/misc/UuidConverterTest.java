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

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

/**
 * Unit tests for {@link UuidConverter}.
 *
 * @author Sascha Knoop - Initial contribution
 */
@DisplayName("UuidConverter")
class UuidConverterTest {

    private UuidConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UuidConverter();
    }

    @Test
    @DisplayName("Convert UUID to bytes and back")
    void testUuidToBytesAndBack() {
        UUID original = UUID.randomUUID();
        byte[] bytes = converter.toBytes(original);
        UUID converted = converter.fromBytes(bytes);
        assertEquals(original, converted);
    }

    @Test
    @DisplayName("Convert UUID with specific values")
    void testUuidWithSpecificValues() {
        UUID uuid = new UUID(0x1234567890ABCDEFL, 0xFEDCBA0987654321L);
        byte[] bytes = converter.toBytes(uuid);
        assertEquals(16, bytes.length);
        UUID converted = converter.fromBytes(bytes);
        assertEquals(uuid, converted);
    }

    @Test
    @DisplayName("Convert ByteString to UUID")
    void testByteStringToUuid() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = converter.toBytes(uuid);
        ByteString byteString = ByteString.copyFrom(bytes);
        UUID converted = converter.fromByteString(byteString);
        assertEquals(uuid, converted);
    }

    @Test
    @DisplayName("Convert bytes with wrong length throws exception")
    void testFromBytesWrongLength() {
        byte[] bytes = new byte[15];
        assertThrows(IllegalArgumentException.class, () -> converter.fromBytes(bytes));
    }

    @Test
    @DisplayName("Convert empty bytes throws exception")
    void testFromBytesEmpty() {
        byte[] bytes = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> converter.fromBytes(bytes));
    }

    @Test
    @DisplayName("Convert 16 zero bytes returns zero UUID")
    void testFromBytesAllZeros() {
        byte[] bytes = new byte[16];
        UUID result = converter.fromBytes(bytes);
        assertEquals(new UUID(0L, 0L), result);
    }
}
