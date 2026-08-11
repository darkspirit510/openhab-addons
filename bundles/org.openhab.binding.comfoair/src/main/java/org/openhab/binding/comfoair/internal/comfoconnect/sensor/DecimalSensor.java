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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
        final ByteBuffer byteBuffer = ByteBuffer.wrap(message.getData().toByteArray()).order(ByteOrder.LITTLE_ENDIAN);

        return switch (type) {
            case TYPE_CN_UINT8 -> unsigned(byteBuffer.get());
            case TYPE_CN_UINT16 -> unsigned(byteBuffer.getShort());
            case TYPE_CN_UINT32 -> unsigned(byteBuffer.getInt());
            case TYPE_CN_INT8 -> byteBuffer.get();
            case TYPE_CN_INT16 -> byteBuffer.getShort();
            case TYPE_CN_INT64 -> byteBuffer.getLong();
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
