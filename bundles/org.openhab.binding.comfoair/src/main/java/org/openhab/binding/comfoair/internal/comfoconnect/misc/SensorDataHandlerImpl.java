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

import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.BitmaskSensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder.CnRpdoNotification;

/**
 * Handles sensor data processing for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorDataHandlerImpl implements SensorDataHandler {

    private final Logger logger = LoggerFactory.getLogger(SensorDataHandlerImpl.class);

    private final Predicate<Sensor> isSensorSubscribedPredicate;
    private final @Nullable ChannelManager channelManager;

    /**
     * Create a new sensor data handler.
     *
     * @param isSensorSubscribedPredicate predicate to check if a sensor is subscribed
     * @param channelManager the channel manager for state updates
     */
    public SensorDataHandlerImpl(final Predicate<Sensor> isSensorSubscribedPredicate,
            final @Nullable ChannelManager channelManager) {
        this.isSensorSubscribedPredicate = isSensorSubscribedPredicate;
        this.channelManager = channelManager;
    }

    @Override
    public void handleSensorData(final Sensor sensor, final CnRpdoNotification notification) {
        // Only process sensor data if this sensor has at least one linked channel
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
     * Check if a sensor is currently subscribed.
     *
     * @param sensor the sensor to check
     * @return true if the sensor has at least one linked channel
     */
    public boolean isSensorSubscribed(final Sensor sensor) {
        return isSensorSubscribedPredicate.test(sensor);
    }
}
