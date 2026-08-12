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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Wrapper class for byte array payloads to avoid passing raw byte arrays around.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class Payload {
    public final byte[] content;
    public final int length;

    /**
     * Create a new Payload from the given byte array.
     *
     * @param content the payload content (must not be null)
     */
    public Payload(byte[] content) {
        this.content = content;
        this.length = content.length;
    }

    /**
     * Get the length of the payload content.
     *
     * @return the length of the content
     */
    public int length() {
        return length;
    }

    /**
     * Extract sensor ID from payload content.
     * Sensor ID is calculated as: (content[2] & 0xFF) << 8 | (content[1] & 0xFF)
     *
     * @return the extracted sensor ID
     */
    public int sensorId() {
        return (content[2] & 0xFF) << 8 | (content[1] & 0xFF);
    }
}
