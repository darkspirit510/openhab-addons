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
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import com.zehnder.proto.Zehnder;

/**
 * Sensor implementation for bypass state values.
 * Maps RMI response values to OnOffType states:
 * - 0x00 (AUTO) -> ON
 * - 0x01 (ON) -> ON
 * - 0x02 (OFF) -> OFF
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class BypassStateSensor extends Sensor {

    public BypassStateSensor(int id, SensorValueType type, String channelId) {
        super(id, type, channelId);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] payload = message.getData().toByteArray();

        if (payload.length < 1) {
            return null;
        }

        // Extract the state value from the payload (single byte for RMI responses)
        int state = payload[0] & 0xFF;

        // 0x00 = AUTO, 0x01 = ON, 0x02 = OFF
        return switch (state) {
            case 0x00 -> OnOffType.ON; // AUTO treated as ON for Switch items
            case 0x01 -> OnOffType.ON;
            case 0x02 -> OnOffType.OFF;
            default -> null; // Unknown state
        };
    }
}
