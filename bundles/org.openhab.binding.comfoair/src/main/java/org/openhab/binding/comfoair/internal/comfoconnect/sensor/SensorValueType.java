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
package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

/**
 * Enum defining ComfoConnect sensor data types.
 *
 * @author Sascha Knoop - Initial contribution
 */
public enum SensorValueType {
    Boolean(0x00),
    UnsignedByte(0x01),
    UnsignedShort(0x02),
    UnsignedInt(0x03),
    SignedByte(0x05),
    SignedShort(0x06),
    SignedLong(0x08),
    String(0x09),
    Timestamp(0x10),
    Version(0x11);

    public final int value;

    SensorValueType(int value) {
        this.value = value;
    }
}
