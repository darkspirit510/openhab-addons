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
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Sensor implementation for numeric (decimal) values.
 * Handles extraction from protobuf messages with proper byte order and data type conversion.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class DecimalSensor extends Sensor {
    private final Logger logger = LoggerFactory.getLogger(DecimalSensor.class);
    private DecimalTransformation transformation = (i) -> i;

    public DecimalSensor(String name, int id, SensorValueType type, String channelId) {
        super(name, id, type, channelId);
    }

    public DecimalSensor withTransformation(DecimalTransformation transformation) {
        this.transformation = transformation;
        return this;
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();
        double rawValue = extractValueFrom(payload);
        double transformedValue = transformation.transform(rawValue);

        logger.info("Sensor {}: raw={}, transformed={}, payload_hex={}", name, rawValue, transformedValue,
                bytesToHex(payload));
        return new DecimalType(transformedValue);
    }

    /**
     * Convert byte array to hex string for logging.
     *
     * @param bytes the byte array to convert
     * @return hex string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF)).append(" ");
        }
        return sb.toString().trim();
    }

    private double extractValueFrom(byte[] payload) {
        return switch (type) {
            case TYPE_CN_UINT8 -> extractUnsignedByte(payload);
            case TYPE_CN_UINT16 -> extractUnsignedShort(payload);
            case TYPE_CN_UINT32 -> extractUnsignedInt(payload);
            case TYPE_CN_INT8 -> extractSignedByte(payload);
            case TYPE_CN_INT16 -> extractSignedShort(payload);
            case TYPE_CN_INT64 -> extractSignedLong(payload);
            default -> 0;
        };
    }

    /**
     * Extract an unsigned 8-bit integer value from the payload.
     *
     * @param payload the payload bytes
     * @return the extracted unsigned byte value
     */
    private double extractUnsignedByte(byte[] payload) {
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
    private double extractUnsignedShort(byte[] payload) {
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
    private double extractUnsignedInt(byte[] payload) {
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
    private double extractSignedByte(byte[] payload) {
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
    private double extractSignedShort(byte[] payload) {
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
    private double extractSignedLong(byte[] payload) {
        if (payload.length >= 8) {
            long value = ((payload[7] & 0xFFL) << 56) | ((payload[6] & 0xFFL) << 48) | ((payload[5] & 0xFFL) << 40)
                    | ((payload[4] & 0xFFL) << 32) | ((payload[3] & 0xFFL) << 24) | ((payload[2] & 0xFFL) << 16)
                    | ((payload[1] & 0xFFL) << 8) | (payload[0] & 0xFFL);
            return value;
        }
        return 0;
    }
}
