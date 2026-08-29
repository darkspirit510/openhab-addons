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

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.openhab.binding.comfoair.internal.comfoconnect.component.BypassStateWorker;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.BitmaskSensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages channel linking/unlinking and sensor tracking for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ChannelManagerImpl implements ChannelManager {

    private final Logger logger = LoggerFactory.getLogger(ChannelManagerImpl.class);

    private final ComfoConnectProtocolHandler protocolHandler;
    private final Thing thing;
    private final Predicate<ChannelUID> isLinkedPredicate;
    private final Supplier<Boolean> isConnectedSupplier;
    private final @Nullable BypassStateWorker bypassStateWorker;
    private final BiConsumer<ChannelUID, State> updateStateCallback;

    // Track which sensors have at least one linked channel
    private final Set<Integer> subscribedSensors = new HashSet<>();

    // Track if we have any linked channels at all
    private int linkedChannelCount = 0;

    /**
     * Create a new channel manager.
     *
     * @param protocolHandler the protocol handler for sensor subscriptions
     * @param thing the thing this manager is associated with
     * @param isLinkedPredicate predicate to check if a channel is linked
     * @param bypassStateWorker the bypass state manager
     * @param isConnectedSupplier supplier to check if connected
     * @param updateStateCallback callback to update channel state
     */
    public ChannelManagerImpl(final ComfoConnectProtocolHandler protocolHandler, final Thing thing,
            final Predicate<ChannelUID> isLinkedPredicate, final @Nullable BypassStateWorker bypassStateWorker,
            final Supplier<Boolean> isConnectedSupplier, final BiConsumer<ChannelUID, State> updateStateCallback) {
        this.protocolHandler = protocolHandler;
        this.thing = thing;
        this.isLinkedPredicate = isLinkedPredicate;
        this.bypassStateWorker = bypassStateWorker;
        this.isConnectedSupplier = isConnectedSupplier;
        this.updateStateCallback = updateStateCallback;
    }

    @Override
    public void channelLinked(final ChannelUID channelUID) {
        String channelId = channelUID.getId();
        // Find the channel object to get the sensor
        thing.getChannels().stream().filter(channel -> channel.getUID().getId().equals(channelId)).findFirst()
                .ifPresentOrElse(channel -> Sensors.sensorForChannel(channel).ifPresentOrElse(sensor -> {
                    logger.debug("Channel {} linked, subscribing to sensor {}", channelId, sensor);

                    // Track that this sensor now has at least one linked channel
                    boolean wasFirstChannel = linkedChannelCount == 0;
                    subscribedSensors.add(sensor.id);
                    linkedChannelCount++;

                    // Always try to subscribe to the sensor if we're connected
                    // The protocol handler will handle duplicate subscriptions gracefully
                    if (isConnected()) {
                        subscribeToSensorForChannel(sensor);

                        // If this is the first channel being linked after all were removed,
                        // resubscribe to all linked sensors to ensure RPDO subscriptions work properly
                        if (wasFirstChannel) {
                            logger.debug(
                                    "First channel linked after all were removed, resubscribing to all linked sensors");
                            resubscribeToAllLinkedSensors();
                        }
                    } else {
                        logger.debug("Not subscribing to sensor {} because not connected", sensor);
                    }

                    // Start polling for bypass state if this is the bypassState channel
                    if (ComfoAirBindingConstants.CHANNEL_BYPASS_STATE.equals(channelId)) {
                        if (this.bypassStateWorker != null) {
                            this.bypassStateWorker.startBypassStatePolling();
                        }
                    }
                }, () -> logger.warn("Channel {} linked but no sensor mapping found", channelId)),
                        () -> logger.warn("Channel {} linked but channel not found", channelId));
    }

    @Override
    public void channelUnlinked(final ChannelUID channelUID) {
        logger.debug("Channel {} unlinked", channelUID.getId());
        String channelId = channelUID.getId();

        // Find the sensor for this channel
        thing.getChannels().stream().filter(channel -> channel.getUID().getId().equals(channelId)).findFirst()
                .ifPresentOrElse(channel -> {
                    // Special handling for BitmaskSensor - find it directly without linking
                    findSensorForChannel(channel).ifPresentOrElse(sensor -> {
                        logger.debug("Channel {} unlinked, checking if sensor {} still has other linked channels",
                                channelId, sensor);

                        // For BitmaskSensor, unlink the channel from the sensor
                        if (sensor instanceof BitmaskSensor bitmaskSensor) {
                            bitmaskSensor.unlinkChannel(channel);
                        }

                        // Check if any other channels for this sensor are still linked
                        boolean stillHasLinkedChannels = thing.getChannels().stream()
                                .filter(ch -> isLinkedPredicate.test(ch.getUID()))
                                .anyMatch(ch -> findSensorForChannel(ch).map(s -> s.id == sensor.id).orElse(false));

                        if (!stillHasLinkedChannels) {
                            // No more channels use this sensor, unsubscribe from it
                            logger.debug("No more linked channels for sensor {}, unsubscribing", sensor);
                            subscribedSensors.remove(sensor.id);
                            unsubscribeFromSensorForChannel(sensor);

                            // Decrement linked channel count
                            linkedChannelCount--;
                        }

                        // Stop polling for bypass state if this is the bypassState channel
                        if (ComfoAirBindingConstants.CHANNEL_BYPASS_STATE.equals(channelId)) {
                            if (this.bypassStateWorker != null) {
                                this.bypassStateWorker.stopBypassStatePolling();
                            }
                        }
                    }, () -> logger.debug("Channel {} unlinked but no sensor mapping found", channelId));
                }, () -> logger.debug("Channel {} unlinked but channel not found", channelId));
    }

    @Override
    public void subscribeToLinkedChannels() {
        logger.debug("Discovering linked channels and subscribing to sensors");

        // Clear any existing subscriptions
        subscribedSensors.clear();
        linkedChannelCount = 0;

        for (Channel channel : thing.getChannels()) {
            if (isLinkedPredicate.test(channel.getUID())) {
                Sensors.sensorForChannel(channel).ifPresent(sensor -> {
                    logger.debug("Channel {} is linked at startup, subscribing to sensor {} ({})",
                            channel.getUID().getId(), sensor.channelId, sensor.id);

                    // Track that this sensor has at least one linked channel
                    subscribedSensors.add(sensor.id);
                    linkedChannelCount++;

                    subscribeToSensorForChannel(sensor);
                    // Start polling for bypass state if this is the bypassState channel
                    if (ComfoAirBindingConstants.CHANNEL_BYPASS_STATE.equals(channel.getUID().getId())) {
                        if (this.bypassStateWorker != null) {
                            this.bypassStateWorker.startBypassStatePolling();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void clearSubscriptions() {
        subscribedSensors.clear();
        linkedChannelCount = 0;
    }

    /**
     * Find the sensor for a channel without linking it.
     * This is used during unlinking to avoid re-linking the channel.
     *
     * @param channel the channel to find the sensor for
     * @return the sensor, or empty if not found
     */
    private java.util.Optional<Sensor> findSensorForChannel(Channel channel) {
        String id = channel.getUID().getId();

        // First, try direct match
        java.util.Optional<Sensor> directMatch = Sensors.knownSensors.stream().filter(s -> id.equals(s.channelId))
                .findFirst();

        if (directMatch.isPresent()) {
            return directMatch;
        }

        // Check if this channel belongs to a BitmaskSensor
        return Sensors.knownSensors.stream().filter(s -> s instanceof BitmaskSensor).map(s -> (BitmaskSensor) s)
                .filter(bitmaskSensor -> bitmaskSensor.getBitsForChannel(id) != null).findFirst()
                .map(bitmaskSensor -> (Sensor) bitmaskSensor);
    }

    /**
     * Check if a sensor is currently subscribed (has at least one linked channel).
     *
     * @param sensor the sensor to check
     * @return true if the sensor has at least one linked channel
     */
    public boolean isSensorSubscribed(Sensor sensor) {
        return subscribedSensors.contains(sensor.id);
    }

    /**
     * Subscribe to a sensor.
     * Calls the appropriate subscription method on the protocol handler.
     *
     * @param sensor the sensor to subscribe to
     */
    private void subscribeToSensorForChannel(Sensor sensor) {
        try {
            protocolHandler.subscribeToSensor(sensor, sensor.type);
        } catch (Exception e) {
            logger.warn("Error subscribing to sensor {}: {}", sensor, e.getMessage());
        }
    }

    /**
     * Unsubscribe from a sensor.
     * Calls the appropriate unsubscription method on the protocol handler.
     *
     * @param sensor the sensor to unsubscribe from
     */
    private void unsubscribeFromSensorForChannel(Sensor sensor) {
        try {
            protocolHandler.unsubscribeFromSensor(sensor);
        } catch (Exception e) {
            logger.warn("Error unsubscribing from sensor {}: {}", sensor, e.getMessage());
        }
    }

    /**
     * Resubscribe to all sensors that have linked channels.
     * This is used when we need to re-establish RPDO subscriptions after they've been unsubscribed.
     */
    private void resubscribeToAllLinkedSensors() {
        logger.debug("Resubscribing to all linked sensors");
        for (Channel channel : thing.getChannels()) {
            if (isLinkedPredicate.test(channel.getUID())) {
                Sensors.sensorForChannel(channel).ifPresent(sensor -> {
                    logger.debug("Resubscribing to sensor {} for channel {}", sensor, channel.getUID().getId());
                    try {
                        protocolHandler.subscribeToSensor(sensor, sensor.type);
                    } catch (Exception e) {
                        logger.warn("Error resubscribing to sensor {}: {}", sensor, e.getMessage());
                    }
                });
            }
        }
    }

    public boolean isConnected() {
        return isConnectedSupplier.get();
    }

    /**
     * Update the state of a channel.
     *
     * @param channelId the channel ID to update
     * @param state the new state for the channel
     */
    public void updateChannelState(final String channelId, final State state) {
        try {
            ChannelUID channelUID = new ChannelUID(thing.getUID(), channelId);
            logger.info("Updating channel {} to state {}", channelId, state);
            updateStateCallback.accept(channelUID, state);
        } catch (Exception e) {
            logger.error("Error updating channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Get the thing this manager is associated with.
     *
     * @return the thing
     */
    public Thing getThing() {
        return thing;
    }
}
