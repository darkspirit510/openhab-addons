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
 * Sensor implementation for manual mode values.
 * Maps manual mode IDs to their string representations.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ManualModeSensor extends Sensor {

    private final Logger logger = LoggerFactory.getLogger(ManualModeSensor.class);

    private static final Map<Integer, String> MANUAL_MODE_MAP = Map.ofEntries(Map.entry(-1, "auto"),
            Map.entry(1, "unlimited_manual"));

    /**
     * Creates a new ManualModeSensor.
     *
     * @param id the sensor ID
     * @param type the sensor value type
     * @param channelId the channel ID
     */
    public ManualModeSensor(int id, SensorValueType type, String channelId) {
        super(id, type, channelId);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();
        int value = extractSignedByte(payload);

        if (!MANUAL_MODE_MAP.containsKey(value)) {
            logger.warn("Unknown manual mode value: {} for sensor {}", value, channelId);

            return null;
        }

        return new StringType(MANUAL_MODE_MAP.get(value));
    }
}
