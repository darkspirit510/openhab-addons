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

    public String name;

    public int id;

    public SensorValueType type;

    public String channelId;

    public @Nullable Channel channel;

    public Sensor(String name, int id, SensorValueType type) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.channelId = "";
        this.channel = null;
    }

    public Sensor(String name, int id, SensorValueType type, String channelId) {
        this.name = name;
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
        return name + " (" + id + ")";
    }
}
