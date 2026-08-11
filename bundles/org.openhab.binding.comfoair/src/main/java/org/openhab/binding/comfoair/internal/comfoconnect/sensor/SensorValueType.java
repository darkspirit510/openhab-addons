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
    TYPE_CN_BOOL(0x00),
    TYPE_CN_UINT8(0x01),
    TYPE_CN_UINT16(0x02),
    TYPE_CN_UINT32(0x03),
    TYPE_CN_INT8(0x05),
    TYPE_CN_INT16(0x06),
    TYPE_CN_INT64(0x08),
    TYPE_CN_STRING(0x09),
    TYPE_CN_TIME(0x10),
    TYPE_CN_VERSION(0x11);

    public final int value;

    SensorValueType(int value) {
        this.value = value;
    }
}
