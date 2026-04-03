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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Bridge handler for ComfoConnect LAN gateway devices (newer Q-series).
 *
 * Manages:
 * - TCP connection to the gateway
 * - Protocol authentication and session management
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectBridgeHandler extends BaseBridgeHandler {

    private static final int CONNECT_TIMEOUT_SEC = 30;
    private static final int CONNECTION_ATTEMPT_DELAY_SEC = 5;

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectBridgeHandler.class);

    private @Nullable ComfoConnectTcpConnector connector;
    private @Nullable ComfoConnectProtocolHandler protocolHandler;
    private @Nullable ScheduledFuture<?> connectionRetryTask;
    private @Nullable Future<?> messageConsumerTask;

    /**
     * Create a new ComfoConnect bridge handler.
     *
     * @param bridge the bridge thing
     */
    public ComfoConnectBridgeHandler(final Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Bridge received command on {}: {}", channelUID, command);
    }

    @Override
    public void initialize() {
        logger.info("Initializing ComfoConnect bridge: {}", getThing().getUID());

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
                gatewayUuid = discoverGatewayUuid(hostname, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
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
            gatewayUuid = discoverGatewayUuid(hostname, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
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

        ComfoConnectProtocolHandler protocolHandler = new ComfoConnectProtocolHandler(connector, pinCode,
                config.autoTakeover, scheduler);
        this.protocolHandler = protocolHandler;

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

            // Start the message consumer loop BEFORE protocol initialization
            // so responses can be received and processed
            startMessageConsumer(connector, protocolHandler);

            protocolHandler.initialize();
            logger.info("ComfoConnect bridge connected and authenticated");

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

        this.connector = null;
        this.protocolHandler = null;
    }

    /**
     * Handle sensor data received from the gateway.
     *
     * @param sensorId the sensor ID
     * @param value the sensor value
     */
    private void handleSensorData(final int sensorId, final int value) {
        logger.debug("handleSensorData called: sensorId={}, value={}", sensorId, value);
        if (sensorId == 65) { // Fan speed sensor
            logger.debug("Processing fan speed sensor data: value={}", value);
            handleFanSpeedUpdate(value);
        } else {
            logger.debug("Ignoring data for unknown sensor: {}", sensorId);
        }
    }

    /**
     * Handle an update to the fan speed value.
     *
     * @param value the fan speed value (0-3)
     */
    private void handleFanSpeedUpdate(final int value) {
        try {
            logger.debug("handleFanSpeedUpdate called: value={}", value);
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), "ventilationSpeed");

            if (value >= 0 && value <= 3) {
                logger.debug("Updating ventilation speed channel to value={}", value);
                updateState(channelUID, new StringType(String.valueOf(value)));
                logger.debug("Ventilation speed channel updated successfully");
            } else {
                logger.warn("Invalid fan speed value: {} (out of range 0-3)", value);
                updateState(channelUID, UnDefType.UNDEF);
            }
        } catch (Exception e) {
            logger.error("Error updating ventilation speed channel: {}", e.getMessage(), e);
        }
    }

    /**
     * Discover the gateway UUID via UDP discovery message sent directly to the gateway host.
     *
     * @param hostname the hostname or IP address of the gateway
     * @param port the UDP port of the gateway
     * @return the discovered gateway UUID, or null if discovery fails
     */
    private @Nullable UUID discoverGatewayUuid(final String hostname, final int port) {
        try {
            logger.debug("Attempting to discover gateway UUID from {}:{}", hostname, port);
            InetAddress gatewayAddress = InetAddress.getByName(hostname);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000); // 5 second timeout for discovery

                // Send discovery message (same format as in ComfoConnectDiscoveryService)
                byte[] discoveryMessage = { 0x0a, 0x00 };
                DatagramPacket sendPacket = new DatagramPacket(discoveryMessage, discoveryMessage.length,
                        gatewayAddress, port);
                socket.send(sendPacket);

                // Receive response
                byte[] buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                // Parse discovery response
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
                        receivePacket.getLength());

                Zehnder.DiscoveryOperation operation = Zehnder.DiscoveryOperation.parseFrom(data);
                if (operation.hasSearchGatewayResponse()) {
                    Zehnder.SearchGatewayResponse response = operation.getSearchGatewayResponse();
                    byte[] uuidBytes = response.getUuid().toByteArray();
                    UUID uuid = bytesToUuid(uuidBytes);
                    logger.info("Gateway UUID discovered: {}", uuid);
                    return uuid;
                }
            }
        } catch (SocketTimeoutException e) {
            logger.debug("Gateway discovery timeout for {}:{}", hostname, port);
        } catch (Exception e) {
            logger.debug("Gateway discovery failed for {}:{} - {}", hostname, port, e.getMessage());
        }
        return null;
    }

    /**
     * Convert 16 bytes to a UUID.
     *
     * @param bytes the 16-byte UUID
     * @return the UUID
     */
    private UUID bytesToUuid(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes long");
        }
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xFF);
            least = (least << 8) | (bytes[8 + i] & 0xFF);
        }
        return new UUID(most, least);
    }

    /**
     * Get the protocol handler for child devices to use.
     *
     * @return the protocol handler, or null if not available
     */
    public @Nullable ComfoConnectProtocolHandler getProtocolHandler() {
        return protocolHandler;
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
}
