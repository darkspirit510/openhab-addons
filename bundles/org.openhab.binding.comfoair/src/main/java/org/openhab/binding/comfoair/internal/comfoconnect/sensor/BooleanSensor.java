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
 * Sensor implementation for boolean values.
 * Converts numeric values to ON/OFF states (0 = OFF, non-zero = ON).
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class BooleanSensor extends Sensor {

    public BooleanSensor(String name, int id, SensorValueType type, String channelId) {
        super(name, id, type, channelId);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        byte[] data = message.getData().toByteArray();

        if (data.length >= 1) {
            int rawValue = data[0] & 0xFF;
            return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
        }

        return null;
    }
}
