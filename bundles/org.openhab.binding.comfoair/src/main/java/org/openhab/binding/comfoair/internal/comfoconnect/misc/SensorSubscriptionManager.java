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
package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType;

/**
 * Interface for managing sensor subscriptions for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface SensorSubscriptionManager {

    /**
     * Subscribe to a sensor.
     *
     * @param sensor the sensor to subscribe to
     * @param sensorType the sensor data type
     */
    void subscribeToSensor(Sensor sensor, SensorValueType sensorType);

    /**
     * Unsubscribe from a sensor.
     *
     * @param sensor the sensor to unsubscribe from
     */
    void unsubscribeFromSensor(Sensor sensor);
}
