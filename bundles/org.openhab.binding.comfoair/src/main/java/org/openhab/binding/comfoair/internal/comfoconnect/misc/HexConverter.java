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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Converts between byte arrays and hexadecimal strings.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class HexConverter {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Convert byte array to hexadecimal string with spaces between bytes.
     *
     * @param bytes the byte array to convert
     * @return hexadecimal string representation, or empty string if bytes is null or empty
     */
    public String toHex(final byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        char[] hexChars = new char[bytes.length * 3 - 1];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 3] = HEX_ARRAY[v >>> 4];
            hexChars[i * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if (i < bytes.length - 1) {
                hexChars[i * 3 + 2] = ' ';
            }
        }
        return new String(hexChars);
    }
}
