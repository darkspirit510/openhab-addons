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

import org.openhab.binding.comfoair.internal.comfoconnect.misc.UuidConverter;

public abstract class Response {
    private static final UuidConverter uuidConverter = new UuidConverter();

    /**
     * Convert 16 bytes to a UUID string.
     *
     * @param bytes the 16-byte UUID
     * @return the UUID as a string
     */
    protected static String bytesToUuid(byte[] bytes) {
        return uuidConverter.fromBytes(bytes).toString();
    }
}
