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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Sensor implementation for bitmask values that exposes individual boolean channels.
 * This sensor handles a 64-bit bitmask (like airflow constraints) and provides separate
 * boolean channels for each bit or bit combination.
 *
 * The sensor manages multiple channels internally and updates them all when the underlying
 * bitmask sensor receives data. The actual channel updates are handled by the ComfoConnectHandler
 * which calls the processBitmaskUpdate method.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class BitmaskSensor extends Sensor {
    private final Logger logger = LoggerFactory.getLogger(BitmaskSensor.class);

    /**
     * Maps channel IDs to their corresponding bit positions.
     * For multi-bit constraints, the channel maps to an array of bits (OR logic).
     */
    private final Map<String, int[]> channelToBitsMap;

    /**
     * Tracks which channels are currently linked.
     */
    private final Set<String> linkedChannels = new HashSet<>();

    /**
     * The validation bit that must be set for constraints to be active.
     * For airflow constraints, this is bit 45.
     */
    private final int validationBit;

    /**
     * Creates a new BitmaskSensor.
     *
     * @param id the sensor ID
     * @param type the sensor value type
     * @param channelId the base channel ID (used for the channel group)
     * @param channelToBitsMap mapping of channel IDs to their bit positions
     * @param validationBit the bit that must be set for constraints to be active
     */
    public BitmaskSensor(int id, SensorValueType type, String channelId, Map<String, int[]> channelToBitsMap,
            int validationBit) {
        super(id, type, channelId);
        this.channelToBitsMap = new HashMap<>(channelToBitsMap);
        this.validationBit = validationBit;
    }

    /**
     * Creates a new BitmaskSensor with default validation bit (45 for airflow constraints).
     *
     * @param id the sensor ID
     * @param type the sensor value type
     * @param channelId the base channel ID (used for the channel group)
     * @param channelToBitsMap mapping of channel IDs to their bit positions
     */
    public BitmaskSensor(int id, SensorValueType type, String channelId, Map<String, int[]> channelToBitsMap) {
        this(id, type, channelId, channelToBitsMap, 45);
    }

    @Override
    public @Nullable State valueAsState(Zehnder.CnRpdoNotification message) {
        // For BitmaskSensor, we don't return a state directly.
        // Instead, the handler will call processBitmaskUpdate to handle all channels.
        // We just extract and return the raw bitmask as a DecimalType for now.
        byte[] payload = message.getData().toByteArray();
        double bitmask = extractSignedLong(payload);
        return new org.openhab.core.library.types.DecimalType((long) bitmask);
    }

    /**
     * Processes the bitmask update and returns the states for all linked channels.
     * This method is called by the ComfoConnectHandler when sensor data is received.
     *
     * @param bitmask the raw bitmask value from the sensor
     * @return a map of channel IDs to their corresponding states
     */
    public Map<String, State> processBitmaskUpdate(long bitmask) {
        Map<String, State> channelStates = new HashMap<>();

        // Validate the validation bit - if not set, constraints are not active
        boolean constraintsActive = (bitmask & (1L << validationBit)) != 0;

        if (!constraintsActive) {
            logger.debug("{} constraints: validation bit {} not set, constraints inactive", channelId, validationBit);
            // Set all linked channels to OFF when constraints are inactive
            for (String channelId : linkedChannels) {
                channelStates.put(channelId, OnOffType.OFF);
            }
            return channelStates;
        }

        // Update all linked channels based on their bit positions
        for (String channelId : linkedChannels) {
            int[] bits = channelToBitsMap.get(channelId);
            if (bits != null && bits.length > 0) {
                boolean isActive = isAnyBitSet(bitmask, bits);
                channelStates.put(channelId, isActive ? OnOffType.ON : OnOffType.OFF);
            }
        }

        return channelStates;
    }

    /**
     * Links a channel to this sensor.
     *
     * @param channel the channel to link
     * @return this sensor for method chaining
     */
    @Override
    public Sensor linkChannel(org.openhab.core.thing.Channel channel) {
        String channelId = channel.getUID().getId();
        linkedChannels.add(channelId);
        logger.debug("Linked channel {}", channelId);
        return this;
    }

    /**
     * Unlinks a channel from this sensor.
     *
     * @param channel the channel to unlink
     */
    public void unlinkChannel(org.openhab.core.thing.Channel channel) {
        String channelId = channel.getUID().getId();
        linkedChannels.remove(channelId);
        logger.debug("Unlinked channel {}", channelId);
    }

    /**
     * Checks if a channel is linked to this sensor.
     *
     * @param channel the channel to check
     * @return true if the channel is linked
     */
    public boolean isChannelLinked(org.openhab.core.thing.Channel channel) {
        return linkedChannels.contains(channel.getUID().getId());
    }

    /**
     * Gets the number of linked channels.
     *
     * @return the number of linked channels
     */
    public int getLinkedChannelCount() {
        return linkedChannels.size();
    }

    /**
     * Gets the set of linked channel IDs.
     *
     * @return the set of linked channel IDs
     */
    public Set<String> getLinkedChannelIds() {
        return new HashSet<>(linkedChannels);
    }

    /**
     * Gets the bit positions for a specific channel.
     *
     * @param channelId the channel ID
     * @return the bit positions for the channel, or null if not found
     */
    public int @Nullable [] getBitsForChannel(String channelId) {
        return channelToBitsMap.get(channelId);
    }

    /**
     * Checks if any of the specified bits are set in the bitmask.
     *
     * @param bitmask the 64-bit bitmask value
     * @param bits the bit positions to check
     * @return true if any of the bits are set
     */
    public boolean isAnyBitSet(long bitmask, int[] bits) {
        for (int bit : bits) {
            if ((bitmask & (1L << bit)) != 0) {
                return true;
            }
        }
        return false;
    }
}
