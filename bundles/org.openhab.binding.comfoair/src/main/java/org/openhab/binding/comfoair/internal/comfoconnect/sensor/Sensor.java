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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder;

/**
 * Abstract base class for all sensor types.
 * Defines the contract for sensors and provides common functionality.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public abstract class Sensor {

    public int id;

    public SensorValueType type;

    public String channelId;

    public @Nullable Channel channel;

    public Sensor(int id, SensorValueType type) {
        this.id = id;
        this.type = type;
        this.channelId = "";
        this.channel = null;
    }

    public Sensor(int id, SensorValueType type, String channelId) {
        this.id = id;
        this.type = type;
        this.channelId = channelId;
        this.channel = null;
    }

    public Sensor linkChannel(Channel channel) {
        this.channel = channel;
        return this;
    }

    /**
     * Convert a sensor notification message to a state.
     *
     * @param message the RPDO notification message
     * @return the state, or null if conversion fails
     */
    public abstract @Nullable State valueAsState(Zehnder.CnRpdoNotification message);

    @Override
    public String toString() {
        return channelId + " (" + id + ")";
    }

    protected Number extractValueFrom(byte[] payload) {
        return switch (type) {
            case UnsignedByte -> extractUnsignedByte(payload);
            case UnsignedShort -> extractUnsignedShort(payload);
            case UnsignedInt -> extractUnsignedInt(payload);
            case SignedByte -> extractSignedByte(payload);
            case SignedShort -> extractSignedShort(payload);
            case SignedLong -> extractSignedLong(payload);
            default -> 0;
        };
    }

    /**
     * Extract an unsigned 8-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted unsigned byte value
     */
    protected int extractUnsignedByte(byte[] payload) {
        if (payload.length >= 1) {
            return (payload[0] & 0xFF);
        }
        return 0;
    }

    /**
     * Extract an unsigned 16-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted unsigned short value
     */
    protected int extractUnsignedShort(byte[] payload) {
        if (payload.length >= 2) {
            return ((payload[1] & 0xFF) << 8) | (payload[0] & 0xFF);
        }
        return 0;
    }

    /**
     * Extract an unsigned 32-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted unsigned int value
     */
    protected long extractUnsignedInt(byte[] payload) {
        if (payload.length >= 4) {
            return ((payload[3] & 0xFFL) << 24) | ((payload[2] & 0xFFL) << 16) | ((payload[1] & 0xFFL) << 8)
                    | (payload[0] & 0xFFL);
        }
        return 0;
    }

    /**
     * Extract a signed 8-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted signed byte value
     */
    protected int extractSignedByte(byte[] payload) {
        if (payload.length >= 1) {
            return payload[0];
        }
        return 0;
    }

    /**
     * Extract a signed 16-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted signed short value
     */
    protected int extractSignedShort(byte[] payload) {
        if (payload.length >= 2) {
            int value = ((payload[1] & 0xFF) << 8) | (payload[0] & 0xFF);
            if ((value & 0x8000) != 0) {
                value -= 0x10000;
            }
            return value;
        }
        return 0;
    }

    /**
     * Extract a signed 64-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted signed long value
     */
    protected long extractSignedLong(byte[] payload) {
        if (payload.length >= 8) {
            return ((payload[7] & 0xFFL) << 56) | ((payload[6] & 0xFFL) << 48) | ((payload[5] & 0xFFL) << 40)
                    | ((payload[4] & 0xFFL) << 32) | ((payload[3] & 0xFFL) << 24) | ((payload[2] & 0xFFL) << 16)
                    | ((payload[1] & 0xFFL) << 8) | (payload[0] & 0xFFL);
        }
        return 0;
    }

    /**
     * Convert byte array to hex string for logging.
     *
     * @param bytes the byte array to convert
     * @return hex string representation
     */
    protected String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF)).append(" ");
        }
        return sb.toString().trim();
    }
}
