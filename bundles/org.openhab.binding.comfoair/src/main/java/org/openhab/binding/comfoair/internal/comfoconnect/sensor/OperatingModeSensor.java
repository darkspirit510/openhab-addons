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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Sensor implementation for operating mode values.
 * Maps operating mode IDs to their string representations.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class OperatingModeSensor extends Sensor {

    private final Logger logger = LoggerFactory.getLogger(OperatingModeSensor.class);

    private static final Map<Integer, String> OPERATING_MODE_MAP = Map.ofEntries(Map.entry(-1, "auto"),
            Map.entry(1, "limited_manual"), Map.entry(5, "unlimited_manual"), Map.entry(6, "boost"),
            Map.entry(11, "away"));

    /**
     * Creates a new OperatingModeSensor.
     *
     * @param id the sensor ID
     * @param type the sensor value type
     * @param channelId the channel ID
     */
    public OperatingModeSensor(int id, SensorValueType type, String channelId) {
        super(id, type, channelId);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();
        int value = extractSignedByte(payload);

        if (!OPERATING_MODE_MAP.containsKey(value)) {
            logger.warn("Unknown operating mode value: {} for sensor {}", value, channelId);

            return null;
        }

        return new StringType(OPERATING_MODE_MAP.get(value));
    }
}
