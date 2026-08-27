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

import java.io.IOException;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages RPDO sensor subscriptions for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorSubscriptionManagerImpl implements SensorSubscriptionManager {

    private final Logger logger = LoggerFactory.getLogger(SensorSubscriptionManagerImpl.class);

    private final ComfoConnectConnector connector;
    private final Consumer<IOException> connectionErrorHandler;

    /**
     * Create a new sensor subscription manager.
     *
     * @param connector the underlying TCP connector
     * @param connectionErrorHandler handler for connection errors
     */
    public SensorSubscriptionManagerImpl(final ComfoConnectConnector connector,
            final Consumer<IOException> connectionErrorHandler) {
        this.connector = connector;
        this.connectionErrorHandler = connectionErrorHandler;
    }

    @Override
    public void subscribeToSensor(final Sensor sensor, final SensorValueType sensorType) {
        try {
            logger.info("Subscribing to sensor {} (PDO {} type {})", sensor, sensor.id, sensorType.value);
            connector.sendRpdoRequest(sensor.id, sensorType.value);
            logger.info("Sensor {} subscription request sent successfully", sensor);
        } catch (IOException e) {
            logger.warn("Failed to subscribe to sensor {}: {}", sensor, e.getMessage());
            // Check if this is a connection-related error and trigger reconnection
            connectionErrorHandler.accept(e);
        }
    }

    @Override
    public void unsubscribeFromSensor(final Sensor sensor) {
        try {
            logger.info("Unsubscribing from sensor {} (PDO {})", sensor, sensor.id);
            connector.sendRpdoUnsubscribe(sensor.id);
            logger.info("Sensor {} unsubscribe request sent successfully", sensor);
        } catch (IOException e) {
            logger.warn("Failed to unsubscribe from sensor {}: {}", sensor, e.getMessage());
            // Check if this is a connection-related error and trigger reconnection
            connectionErrorHandler.accept(e);
        }
    }
}
