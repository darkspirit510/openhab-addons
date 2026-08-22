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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HexConverter}.
 *
 * @author Sascha Knoop - Initial contribution
 */
@DisplayName("HexConverter")
public class HexConverterTest {

    private HexConverter converter;

    @BeforeEach
    public void setUp() {
        converter = new HexConverter();
    }

    @Test
    @DisplayName("Convert empty byte array returns empty string")
    public void testToHexWithEmptyArray() {
        String result = converter.toHex(new byte[0]);
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("Convert null byte array returns empty string")
    public void testToHexWithNull() {
        String result = converter.toHex(null);
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("Convert single byte to hex")
    public void testToHexSingleByte() {
        byte[] bytes = new byte[] { (byte) 0xFF };
        String result = converter.toHex(bytes);
        assertEquals("FF", result);
    }

    @Test
    @DisplayName("Convert single byte 0x00 to hex")
    public void testToHexSingleByteZero() {
        byte[] bytes = new byte[] { 0x00 };
        String result = converter.toHex(bytes);
        assertEquals("00", result);
    }

    @Test
    @DisplayName("Convert multiple bytes to hex with spaces")
    public void testToHexMultipleBytes() {
        byte[] bytes = new byte[] { 0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF };
        String result = converter.toHex(bytes);
        assertEquals("01 23 45 67 89 AB CD EF", result);
    }

    @Test
    @DisplayName("Convert all zeros to hex")
    public void testToHexAllZeros() {
        byte[] bytes = new byte[] { 0x00, 0x00, 0x00 };
        String result = converter.toHex(bytes);
        assertEquals("00 00 00", result);
    }

    @Test
    @DisplayName("Convert all FF to hex")
    public void testToHexAllFF() {
        byte[] bytes = new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        String result = converter.toHex(bytes);
        assertEquals("FF FF FF", result);
    }

    @Test
    @DisplayName("Convert alternating pattern to hex")
    public void testToHexAlternatingPattern() {
        byte[] bytes = new byte[] { 0x00, (byte) 0xFF, 0x00, (byte) 0xFF };
        String result = converter.toHex(bytes);
        assertEquals("00 FF 00 FF", result);
    }

    @Test
    @DisplayName("Convert byte values 0x10 to hex")
    public void testToHexWith0x10() {
        byte[] bytes = new byte[] { 0x10 };
        String result = converter.toHex(bytes);
        assertEquals("10", result);
    }

    @Test
    @DisplayName("Convert negative byte values to hex")
    public void testToHexNegativeBytes() {
        byte[] bytes = new byte[] { (byte) 0x80, (byte) 0xFF };
        String result = converter.toHex(bytes);
        assertEquals("80 FF", result);
    }

    @Test
    @DisplayName("Convert larger byte array to hex")
    public void testToHexLargerArray() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) i;
        }
        String result = converter.toHex(bytes);
        assertEquals("00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F", result);
    }

    @Test
    @DisplayName("Convert bytes with values > 127 to hex")
    public void testToHexValuesAbove127() {
        byte[] bytes = new byte[] { (byte) 0x80, (byte) 0x90, (byte) 0xA0, (byte) 0xB0, (byte) 0xC0, (byte) 0xD0,
                (byte) 0xE0, (byte) 0xF0 };
        String result = converter.toHex(bytes);
        assertEquals("80 90 A0 B0 C0 D0 E0 F0", result);
    }

    @Test
    @DisplayName("Convert mixed positive and negative bytes to hex")
    public void testToHexMixedBytes() {
        byte[] bytes = new byte[] { 0x01, (byte) 0x82, 0x03, (byte) 0x84 };
        String result = converter.toHex(bytes);
        assertEquals("01 82 03 84", result);
    }

    @Test
    @DisplayName("Verify hex output uses uppercase letters")
    public void testToHexUppercase() {
        byte[] bytes = new byte[] { 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };
        String result = converter.toHex(bytes);
        assertEquals("0A 0B 0C 0D 0E 0F", result);
        assertEquals(result, result.toUpperCase());
    }
}
