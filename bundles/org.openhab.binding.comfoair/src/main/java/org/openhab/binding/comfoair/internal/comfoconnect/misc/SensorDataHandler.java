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

import com.zehnder.proto.Zehnder.CnRpdoNotification;

/**
 * Interface for handling sensor data for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface SensorDataHandler {

    /**
     * Handle sensor data received from the gateway.
     *
     * @param sensor the sensor that received data
     * @param notification the RPDO notification containing the data
     */
    void handleSensorData(org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor sensor,
            CnRpdoNotification notification);
}
