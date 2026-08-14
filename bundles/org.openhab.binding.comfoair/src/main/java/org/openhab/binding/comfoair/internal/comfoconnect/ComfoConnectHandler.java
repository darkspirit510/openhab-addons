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
package org.openhab.binding.comfoair.internal.comfoconnect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.binding.comfoair.internal.comfoconnect.response.SearchGatewayResponse;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Handler for ComfoConnect LAN gateway devices (newer Q-series).
 *
 * Manages:
 * - TCP connection to the gateway
 * - Protocol authentication and session management
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectHandler extends BaseThingHandler {

    private static final int CONNECTION_ATTEMPT_DELAY_SEC = 5;
    private static final int BYPASS_STATE_POLL_INTERVAL_SEC = 30;

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectHandler.class);

    private @Nullable ComfoConnectTcpConnector connector;
    private @Nullable ComfoConnectProtocolHandler protocolHandler;
    private @Nullable ScheduledFuture<?> connectionRetryTask;
    private @Nullable Future<?> messageConsumerTask;
    private @Nullable ScheduledFuture<?> bypassStatePollingTask;

    // Track which sensors have at least one linked channel
    private final Set<Integer> subscribedSensors = new HashSet<>();

    // Track if we have any linked channels at all
    private int linkedChannelCount = 0;

    /**
     * Create a new ComfoConnect handler.
     *
     * @param thing the thing
     */
    public ComfoConnectHandler(final Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("ComfoConnect handler received command on {}: {}", channelUID, command);
    }

    @Override
    public void initialize() {
        logger.info("Initializing ComfoConnect device: {}", getThing().getUID());

        ComfoConnectConfiguration config = getConfigAs(ComfoConnectConfiguration.class);

        if (config.hostname == null || config.hostname.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Gateway hostname is not configured");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        String hostname = Objects.requireNonNull(config.hostname);

        // Use configured clientUuid or default from constant
        UUID clientUuid;

        if (config.clientUuid != null && !config.clientUuid.isEmpty()) {
            try {
                clientUuid = UUID.fromString(Objects.requireNonNull(config.clientUuid));
                logger.debug("Using configured client UUID: {}", clientUuid);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid client UUID format, using default: {}", config.clientUuid);
                clientUuid = UUID.fromString(ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID);
            }
        } else {
            clientUuid = UUID.fromString(ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID);
            logger.debug("Using default client UUID: {}", clientUuid);
        }

        // Use gateway UUID from discovery or derive it as fallback
        UUID gatewayUuid;

        if (config.gatewayUuid != null && !config.gatewayUuid.isEmpty()) {
            try {
                gatewayUuid = UUID.fromString(Objects.requireNonNull(config.gatewayUuid));
                logger.debug("Using configured gateway UUID: {}", gatewayUuid);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid gateway UUID format, attempting discovery: {}", config.gatewayUuid);
                gatewayUuid = discoverGatewayUuid(hostname);

                if (gatewayUuid == null) {
                    // Discovery failed, use fallback
                    logger.warn("Gateway discovery failed, using derived UUID as fallback");
                    gatewayUuid = UUID.nameUUIDFromBytes(
                            ("comfoair-gateway-" + hostname + ":" + ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT)
                                    .getBytes());
                }
            }
        } else {
            logger.debug("No gateway UUID configured, attempting discovery");
            gatewayUuid = discoverGatewayUuid(hostname);

            if (gatewayUuid == null) {
                // Discovery failed, use fallback
                logger.debug("Gateway discovery failed, using derived UUID as fallback");
                gatewayUuid = UUID.nameUUIDFromBytes(
                        ("comfoair-gateway-" + hostname + ":" + ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT)
                                .getBytes());
            }
        }

        ComfoConnectTcpConnector connector = new ComfoConnectTcpConnector(hostname,
                ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT, clientUuid, gatewayUuid);
        this.connector = connector;

        int pinCode;

        if (config.pin == null || config.pin.isEmpty()) {
            pinCode = 0; // Default PIN when not configured
            logger.debug("Using default PIN (0) as none was configured");
        } else {
            try {
                pinCode = Integer.parseInt(Objects.requireNonNull(config.pin));
            } catch (NumberFormatException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Gateway PIN must be a numeric value");
                return;
            }
        }

        this.protocolHandler = new ComfoConnectProtocolHandler(connector, pinCode, config.autoTakeover, scheduler);

        scheduler.submit(this::connect);
    }

    /**
     * Attempt to connect and authenticate with the gateway.
     */
    private void connect() {
        ComfoConnectTcpConnector connector = this.connector;
        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;

        if (connector == null || protocolHandler == null) {
            logger.error("Connector or protocol handler not initialized");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Internal error: handlers not initialized");
            return;
        }

        try {
            logger.debug("Attempting to connect to ComfoConnect gateway");
            connector.connect();
            logger.debug("TCP connection established, initializing protocol");

            // Register sensor data callback before protocol initialization
            protocolHandler.setSensorCallback(this::handleSensorData);

            // Register keep-alive failure callback
            protocolHandler.setKeepAliveFailureCallback(this::handleKeepAliveFailure);

            // Register connection error callback for automatic reconnection
            protocolHandler.setConnectionErrorCallback(this::handleConnectionError);

            // Start the message consumer loop BEFORE protocol initialization
            // so responses can be received and processed
            startMessageConsumer(connector, protocolHandler);

            protocolHandler.initialize();
            logger.info("ComfoConnect bridge connected and authenticated");

            // Subscribe to sensors based on linked channels
            subscribeToLinkedChannels();

            updateStatus(ThingStatus.ONLINE);

            ScheduledFuture<?> task = connectionRetryTask;

            if (task != null) {
                task.cancel(true);
                connectionRetryTask = null;
            }
        } catch (InterruptedException e) {
            logger.warn("Connection attempt interrupted: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection interrupted");
            scheduleReconnectAttempt();
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Connection timeout: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection timeout");
            scheduleReconnectAttempt();
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            ThingStatusDetail detail = ThingStatusDetail.COMMUNICATION_ERROR;

            // Provide more specific error messages
            if (errorMsg != null && errorMsg.contains("Invalid PIN")) {
                detail = ThingStatusDetail.CONFIGURATION_ERROR;
                errorMsg = "Invalid PIN code (NOT_ALLOWED)";
            } else if (errorMsg != null && errorMsg.contains("Another app is already logged in")) {
                detail = ThingStatusDetail.CONFIGURATION_ERROR;
            }

            logger.warn("Failed to connect or authenticate with gateway: {}", errorMsg);
            updateStatus(ThingStatus.OFFLINE, detail, errorMsg);
            scheduleReconnectAttempt();
        }
    }

    /**
     * Schedule a reconnection attempt after a delay.
     */
    private void scheduleReconnectAttempt() {
        ScheduledFuture<?> task = connectionRetryTask;

        if (task != null && !task.isDone()) {
            return; // Retry already scheduled
        }

        logger.debug("Scheduling reconnection attempt in {} seconds", CONNECTION_ATTEMPT_DELAY_SEC);
        connectionRetryTask = scheduler.schedule(this::connect, CONNECTION_ATTEMPT_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Start the message consumer loop that processes incoming messages from the gateway.
     * This task runs in the background and continuously polls for messages from the connector's queue,
     * then dispatches them to the protocol handler for processing.
     *
     * @param connector the TCP connector with queued messages
     * @param protocolHandler the protocol handler to process messages
     */
    private void startMessageConsumer(final ComfoConnectTcpConnector connector,
            final ComfoConnectProtocolHandler protocolHandler) {
        logger.debug("Starting message consumer loop");

        messageConsumerTask = scheduler.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] message = connector.getNextMessage();

                    if (message != null) {
                        logger.trace("Message consumer: processing {} bytes", message.length);
                        protocolHandler.handleIncomingMessage(message);
                    }
                }
            } catch (Exception e) {
                logger.warn("Unexpected error in message consumer loop: {}", e.getMessage(), e);
            }
            logger.debug("Message consumer loop stopped");
        });
    }

    @Override
    public void dispose() {
        logger.info("Disposing ComfoConnect bridge: {}", getThing().getUID());

        // Unregister sensor callback
        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;

        if (protocolHandler != null) {
            protocolHandler.setSensorCallback(null);
        }

        ScheduledFuture<?> task = connectionRetryTask;

        if (task != null) {
            task.cancel(true);
            connectionRetryTask = null;
        }

        Future<?> consumerTask = messageConsumerTask;

        if (consumerTask != null) {
            consumerTask.cancel(true);
            messageConsumerTask = null;
        }

        if (protocolHandler != null) {
            protocolHandler.shutdown();
        }

        ComfoConnectTcpConnector connector = this.connector;

        if (connector != null) {
            connector.disconnect();
        }

        // Stop bypass state polling
        stopBypassStatePolling();

        // Clear subscribed sensors and channel count
        subscribedSensors.clear();
        linkedChannelCount = 0;

        this.connector = null;
        this.protocolHandler = null;
    }

    /**
     * Handle sensor data received from the gateway.
     *
     * @param sensor the sensor object
     * @param message the protobuf message containing sensor data
     */
    private void handleSensorData(final Sensor sensor, final Zehnder.CnRpdoNotification message) {
        // Only process sensor data if this sensor has at least one linked channel
        if (!subscribedSensors.contains(sensor.id)) {
            logger.debug("Ignoring sensor data for unsubscribed sensor: {}", sensor);
            return;
        }

        logger.debug("handleSensorData called: sensor={}", sensor);

        State state = sensor.valueAsState(message);

        if (state != null) {
            // Update the channel state using the sensor's channel ID
            updateChannelState(sensor.channelId, state);
        } else {
            logger.debug("Ignoring data for unknown sensor: {}", sensor);
        }
    }

    /**
     * Update the state of a channel on this bridge.
     *
     * @param channelId the channel ID to update
     * @param state the new state for the channel
     */
    private void updateChannelState(final String channelId, final org.openhab.core.types.State state) {
        try {
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), channelId);
            logger.info("Updating channel {} to state {}", channelId, state);
            updateState(channelUID, state);
        } catch (Exception e) {
            logger.error("Error updating channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Discover the gateway UUID via UDP discovery message sent directly to the gateway host.
     *
     * @param hostname the hostname or IP address of the gateway
     * @return the discovered gateway UUID, or null if discovery fails
     */
    private @Nullable UUID discoverGatewayUuid(final String hostname) {
        try {
            logger.debug("Attempting to discover gateway UUID from {}:{}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
            InetAddress gatewayAddress = InetAddress.getByName(hostname);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000); // 5 second timeout for discovery

                // Send discovery message (same format as in ComfoConnectDiscoveryService)
                byte[] discoveryMessage = { 0x0a, 0x00 };
                DatagramPacket sendPacket = new DatagramPacket(discoveryMessage, discoveryMessage.length,
                        gatewayAddress, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
                socket.send(sendPacket);

                // Receive response
                byte[] buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                // Parse discovery response
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
                        receivePacket.getLength());

                SearchGatewayResponse searchResponse = SearchGatewayResponse.from(data);

                if (searchResponse != null) {
                    UUID uuid = UUID.fromString(searchResponse.getUuid());
                    logger.info("Gateway UUID discovered: {}", uuid);

                    return uuid;
                }
            }
        } catch (SocketTimeoutException e) {
            logger.debug("Gateway discovery timeout for {}:{}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
        } catch (Exception e) {
            logger.debug("Gateway discovery failed for {}:{} - {}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT, e.getMessage());
        }

        return null;
    }

    /**
     * Check if the bridge is currently connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        ComfoConnectTcpConnector connector = this.connector;
        return connector != null && connector.isConnected();
    }

    /**
     * Resubscribe to all sensors that have linked channels.
     * This is used when we need to re-establish RPDO subscriptions after they've been unsubscribed.
     */
    private void resubscribeToAllLinkedSensors() {
        ComfoConnectProtocolHandler handler = protocolHandler;
        if (handler == null) {
            logger.warn("Cannot resubscribe to sensors: protocol handler not initialized");
            return;
        }

        logger.debug("Resubscribing to all linked sensors");
        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID())) {
                Sensors.sensorForChannel(channel).ifPresent(sensor -> {
                    logger.debug("Resubscribing to sensor {} for channel {}", sensor, channel.getUID().getId());
                    try {
                        handler.subscribeToSensor(sensor, sensor.type);
                    } catch (Exception e) {
                        logger.warn("Error resubscribing to sensor {}: {}", sensor, e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Called when a channel is linked to an item.
     * Subscribes to the corresponding sensor if not already subscribed.
     *
     * @param channelUID the UID of the linked channel
     */
    @Override
    public void channelLinked(ChannelUID channelUID) {
        String channelId = channelUID.getId();
        // Find the channel object to get the sensor
        getThing().getChannels().stream().filter(channel -> channel.getUID().getId().equals(channelId)).findFirst()
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
                            logger.info(
                                    "First channel linked after all were removed, resubscribing to all linked sensors");
                            resubscribeToAllLinkedSensors();
                        }
                    } else {
                        logger.debug("Not subscribing to sensor {} because not connected", sensor);
                    }

                    // Start polling for bypass state if this is the bypassState channel
                    if (ComfoAirBindingConstants.CHANNEL_BYPASS_STATE.equals(channelId)) {
                        startBypassStatePolling();
                    }
                }, () -> logger.warn("Channel {} linked but no sensor mapping found", channelId)),
                        () -> logger.warn("Channel {} linked but channel not found", channelId));
    }

    /**
     * Called when a channel is unlinked from an item.
     *
     * @param channelUID the UID of the unlinked channel
     */
    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        logger.debug("Channel {} unlinked", channelUID.getId());
        String channelId = channelUID.getId();

        // Find the sensor for this channel
        getThing().getChannels().stream().filter(channel -> channel.getUID().getId().equals(channelId)).findFirst()
                .ifPresentOrElse(channel -> {
                    Sensors.sensorForChannel(channel).ifPresentOrElse(sensor -> {
                        logger.debug("Channel {} unlinked, checking if sensor {} still has other linked channels",
                                channelId, sensor);

                        // Check if any other channels for this sensor are still linked
                        boolean stillHasLinkedChannels = getThing().getChannels().stream()
                                .filter(ch -> isLinked(ch.getUID()))
                                .anyMatch(ch -> Sensors.sensorForChannel(ch).map(s -> s.id == sensor.id).orElse(false));

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
                            stopBypassStatePolling();
                        }
                    }, () -> logger.debug("Channel {} unlinked but no sensor mapping found", channelId));
                }, () -> logger.debug("Channel {} unlinked but channel not found", channelId));
    }

    /**
     * Subscribe to all sensors that have linked channels.
     * Called during bridge initialization to discover which channels are linked
     * and subscribe only to their corresponding sensors.
     */
    private void subscribeToLinkedChannels() {
        logger.debug("Discovering linked channels and subscribing to sensors");

        // Clear any existing subscriptions
        subscribedSensors.clear();
        linkedChannelCount = 0;

        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID())) {
                Sensors.sensorForChannel(channel).ifPresent(sensor -> {
                    logger.debug("Channel {} is linked at startup, subscribing to sensor {} ({})",
                            channel.getUID().getId(), sensor.name, sensor.id);

                    // Track that this sensor has at least one linked channel
                    subscribedSensors.add(sensor.id);
                    linkedChannelCount++;

                    subscribeToSensorForChannel(sensor);
                    // Start polling for bypass state if this is the bypassState channel
                    if (ComfoAirBindingConstants.CHANNEL_BYPASS_STATE.equals(channel.getUID().getId())) {
                        startBypassStatePolling();
                    }
                });
            }
        }
    }

    /**
     * Check if a sensor is currently subscribed (has at least one linked channel).
     *
     * @param sensor the sensor to check
     * @return true if the sensor has at least one linked channel
     */
    private boolean isSensorSubscribed(Sensor sensor) {
        return subscribedSensors.contains(sensor.id);
    }

    /**
     * Subscribe to a sensor.
     * Calls the appropriate subscription method on the protocol handler.
     *
     * @param sensor the sensor to subscribe to
     */
    private void subscribeToSensorForChannel(Sensor sensor) {
        ComfoConnectProtocolHandler handler = protocolHandler;
        if (handler == null) {
            logger.warn("Cannot subscribe to sensor {}: protocol handler not initialized", sensor);
            return;
        }

        try {
            handler.subscribeToSensor(sensor, sensor.type);
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
        ComfoConnectProtocolHandler handler = protocolHandler;
        if (handler == null) {
            logger.warn("Cannot unsubscribe from sensor {}: protocol handler not initialized", sensor);
            return;
        }

        try {
            handler.unsubscribeFromSensor(sensor);
        } catch (Exception e) {
            logger.warn("Error unsubscribing from sensor {}: {}", sensor, e.getMessage());
        }
    }

    /**
     * Handle keep-alive failure by marking bridge offline and scheduling a fresh reconnection.
     */
    private void handleKeepAliveFailure() {
        logger.warn("Keep-alive timeout detected, attempting fresh connection");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Gateway connection lost: Keep-alive timeout");
        ComfoConnectProtocolHandler handler = protocolHandler;

        if (handler != null) {
            handler.stopKeepAliveTimer();
        }

        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    /**
     * Handle connection errors by marking bridge offline and scheduling a fresh reconnection.
     */
    private void handleConnectionError() {
        logger.warn("Connection error detected, attempting fresh connection");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "Gateway connection lost: Communication error");

        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    /**
     * Start polling for bypass state via RMI requests.
     */
    private void startBypassStatePolling() {
        if (bypassStatePollingTask != null) {
            return; // Already running
        }

        logger.debug("Starting bypass state polling every {} seconds", BYPASS_STATE_POLL_INTERVAL_SEC);
        bypassStatePollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                ComfoConnectProtocolHandler handler = protocolHandler;
                if (handler != null && isConnected()) {
                    handler.sendRmiRequest(ComfoAirBindingConstants.RMI_UNIT_SCHEDULE,
                            ComfoAirBindingConstants.RMI_SUBUNIT_02,
                            ComfoAirBindingConstants.RMI_PROPERTY_BYPASS_STATE);
                }
            } catch (Exception e) {
                logger.warn("Error polling bypass state: {}", e.getMessage());
            }
        }, 0, BYPASS_STATE_POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Stop polling for bypass state.
     */
    private void stopBypassStatePolling() {
        ScheduledFuture<?> task = bypassStatePollingTask;
        if (task != null) {
            task.cancel(true);
            bypassStatePollingTask = null;
            logger.debug("Stopped bypass state polling");
        }
    }
}
