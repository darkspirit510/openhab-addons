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
 * Sensor implementation for numeric (decimal) values that need to be divided by 10.
 * Handles extraction from protobuf messages with proper byte order and data type conversion.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class TenthDecimalSensor extends DecimalSensor {
    private final Logger logger = LoggerFactory.getLogger(TenthDecimalSensor.class);

    public TenthDecimalSensor(int id, SensorValueType type, String channelId) {
        super(id, type, channelId);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();
        double rawValue = extractValueFrom(payload).doubleValue();
        double transformedValue = rawValue / 10.0;

        logger.debug("Sensor {}: raw={}, transformed={}, payload_hex={}", channelId, rawValue, transformedValue,
                bytesToHex(payload));

        return new DecimalType(transformedValue);
    }
}
