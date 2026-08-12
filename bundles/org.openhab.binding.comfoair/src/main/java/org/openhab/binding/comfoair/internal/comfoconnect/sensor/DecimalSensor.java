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

import com.zehnder.proto.Zehnder;

/**
 * Sensor implementation for numeric (decimal) values.
 * Handles extraction from protobuf messages with proper byte order and data type conversion.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class DecimalSensor extends Sensor {
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
        return new DecimalType(transformation.transform(extractValueFrom(message)));
    }

    private long extractValueFrom(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();

        if (payload.length < 4) {
            return 0;
        }

        return switch (type) {
            case TYPE_CN_UINT8 -> payload[3] & 0xFF;
            case TYPE_CN_UINT16 -> {
                if (payload.length >= 7) {
                    yield ((payload[6] & 0xFF) << 8) | (payload[5] & 0xFF);
                }
                yield 0;
            }
            case TYPE_CN_UINT32 -> {
                if (payload.length >= 8) {
                    yield ((payload[7] & 0xFFL) << 24) | ((payload[6] & 0xFFL) << 16) | ((payload[5] & 0xFFL) << 8)
                            | (payload[4] & 0xFFL);
                }
                yield 0;
            }
            case TYPE_CN_INT8 -> payload[3];
            case TYPE_CN_INT16 -> {
                if (payload.length >= 7) {
                    int value = ((payload[6] & 0xFF) << 8) | (payload[5] & 0xFF);
                    if ((value & 0x8000) != 0) {
                        value -= 0x10000;
                    }
                    yield value;
                }
                yield 0;
            }
            case TYPE_CN_INT64 -> {
                if (payload.length >= 10) {
                    yield ((payload[9] & 0xFFL) << 56) | ((payload[8] & 0xFFL) << 48) | ((payload[7] & 0xFFL) << 40)
                            | ((payload[6] & 0xFFL) << 32) | ((payload[5] & 0xFFL) << 24) | ((payload[4] & 0xFFL) << 16)
                            | ((payload[3] & 0xFFL) << 8) | (payload[2] & 0xFFL);
                }
                yield 0;
            }
            default -> 0;
        };
    }

    private static int unsigned(byte value) {
        return value & 0x000000FF;
    }

    private static int unsigned(short value) {
        return value & 0x0000FFFF;
    }

    private static long unsigned(int value) {
        return value & 0x00000000FFFFFFFFL;
    }
}
