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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.BitmaskSensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder.CnRpdoNotification;

/**
 * Manages sensor subscriptions and handles sensor data for ComfoConnect protocol.
 * Combines the functionality of the previous SensorDataHandler and SensorSubscriptionManager.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorManagerImpl {

    private final Logger logger = LoggerFactory.getLogger(SensorManagerImpl.class);

    private final ComfoConnectConnector connector;
    private final Consumer<IOException> connectionErrorHandler;
    private final @Nullable ChannelManager channelManager;

    private final Set<Integer> subscribedSensorIds = ConcurrentHashMap.newKeySet();

    /**
     * Create a new sensor manager.
     *
     * @param connector the underlying TCP connector
     * @param connectionErrorHandler handler for connection errors
     * @param channelManager the channel manager for state updates
     */
    public SensorManagerImpl(final ComfoConnectConnector connector, final Consumer<IOException> connectionErrorHandler,
            final @Nullable ChannelManager channelManager) {
        this.connector = connector;
        this.connectionErrorHandler = connectionErrorHandler;
        this.channelManager = channelManager;
    }

    /**
     * Handle sensor data received from the gateway.
     *
     * @param sensor the sensor that received data
     * @param notification the RPDO notification containing the data
     */
    public void handleSensorData(final Sensor sensor, final CnRpdoNotification notification) {
        // Only process sensor data if this sensor is subscribed
        if (!isSensorSubscribed(sensor)) {
            logger.debug("Ignoring sensor data for unsubscribed sensor: {}", sensor);
            return;
        }

        logger.debug("handleSensorData called: sensor={}", sensor);

        ChannelManager channelMgr = this.channelManager;

        // Special handling for BitmaskSensor
        if (sensor instanceof BitmaskSensor bitmaskSensor) {
            State state = sensor.valueAsState(notification);
            if (state instanceof DecimalType decimalState) {
                long bitmask = decimalState.longValue();
                // Process the bitmask and get states for all linked channels
                Map<String, State> channelStates = bitmaskSensor.processBitmaskUpdate(bitmask);

                // Update each channel with its corresponding state
                if (channelMgr != null) {
                    for (Map.Entry<String, State> entry : channelStates.entrySet()) {
                        channelMgr.updateChannelState(entry.getKey(), entry.getValue());
                    }
                }
            }
            return;
        }

        // Normal sensor handling
        State state = sensor.valueAsState(notification);

        if (state != null && channelMgr != null) {
            // Update the channel state using the sensor's channel ID
            channelMgr.updateChannelState(sensor.channelId, state);
        } else {
            logger.debug("Ignoring data for unknown sensor: {}", sensor);
        }
    }

    /**
     * Subscribe to a sensor.
     *
     * @param sensor the sensor to subscribe to
     * @param sensorType the sensor data type
     */
    public void subscribeToSensor(final Sensor sensor, final SensorValueType sensorType) {
        try {
            logger.info("Subscribing to sensor {} (PDO {} type {})", sensor, sensor.id, sensorType.value);
            connector.sendRpdoRequest(sensor.id, sensorType.value);
            subscribedSensorIds.add(sensor.id);
            logger.info("Sensor {} subscription request sent successfully", sensor);
        } catch (IOException e) {
            logger.warn("Failed to subscribe to sensor {}: {}", sensor, e.getMessage());
            // Check if this is a connection-related error and trigger reconnection
            connectionErrorHandler.accept(e);
        }
    }

    /**
     * Unsubscribe from a sensor.
     *
     * @param sensor the sensor to unsubscribe from
     */
    public void unsubscribeFromSensor(final Sensor sensor) {
        try {
            logger.info("Unsubscribing from sensor {} (PDO {})", sensor, sensor.id);
            connector.sendRpdoUnsubscribe(sensor.id);
            subscribedSensorIds.remove(sensor.id);
            logger.info("Sensor {} unsubscribe request sent successfully", sensor);
        } catch (IOException e) {
            logger.warn("Failed to unsubscribe from sensor {}: {}", sensor, e.getMessage());
            // Check if this is a connection-related error and trigger reconnection
            connectionErrorHandler.accept(e);
        }
    }

    /**
     * Check if a sensor is currently subscribed.
     *
     * @param sensor the sensor to check
     * @return true if the sensor is subscribed
     */
    public boolean isSensorSubscribed(final Sensor sensor) {
        return subscribedSensorIds.contains(sensor.id);
    }

    /**
     * Clear all subscriptions.
     */
    public void clearSubscriptions() {
        subscribedSensorIds.clear();
    }
}
