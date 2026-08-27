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
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.State;

/**
 * Interface for managing channel subscriptions for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface ChannelManager {

    /**
     * Called when a channel is linked to an item.
     *
     * @param channelUID the UID of the linked channel
     */
    void channelLinked(ChannelUID channelUID);

    /**
     * Called when a channel is unlinked from an item.
     *
     * @param channelUID the UID of the unlinked channel
     */
    void channelUnlinked(ChannelUID channelUID);

    /**
     * Subscribe to all linked channels.
     */
    void subscribeToLinkedChannels();

    /**
     * Clear all subscriptions.
     */
    void clearSubscriptions();

    /**
     * Update the state of a channel.
     *
     * @param channelId the channel ID to update
     * @param state the new state for the channel
     */
    void updateChannelState(String channelId, State state);

    /**
     * Get the thing this manager is associated with.
     *
     * @return the thing
     */
    Thing getThing();

    /**
     * Check if a sensor is currently subscribed.
     *
     * @param sensor the sensor to check
     * @return true if the sensor has at least one linked channel
     */
    boolean isSensorSubscribed(org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor sensor);
}
