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
import java.util.Objects;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.binding.comfoair.internal.comfoconnect.component.BypassStateWorker;
import org.openhab.binding.comfoair.internal.comfoconnect.component.Gateway;
import org.openhab.binding.comfoair.internal.comfoconnect.component.MessageQueue;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ChannelManager;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ChannelManagerImpl;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ConnectionManager;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ConnectionManagerImpl;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.HexConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.SensorManagerImpl;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectHandler.class);

    private @Nullable ComfoConnectConnector connector;
    private @Nullable ComfoConnectProtocolHandler protocolHandler;
    private final Gateway gateway = new Gateway();
    private @Nullable SensorManagerImpl sensorManager;

    private @Nullable MessageQueue messageQueue;
    private @Nullable BypassStateWorker bypassStateWorker;
    private @Nullable ConnectionManager connectionManager;
    private @Nullable ChannelManager channelManager;

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
                gatewayUuid = gateway.discoverUuid(hostname);

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
            gatewayUuid = gateway.discoverUuid(hostname);

            if (gatewayUuid == null) {
                // Discovery failed, use fallback
                logger.debug("Gateway discovery failed, using derived UUID as fallback");
                gatewayUuid = UUID.nameUUIDFromBytes(
                        ("comfoair-gateway-" + hostname + ":" + ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT)
                                .getBytes());
            }
        }

        ComfoConnectConnector connector = new ComfoConnectConnector(hostname,
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

        // Create Messages instance with all dependencies
        ComfoConnectProtocolHandler handler = Objects.requireNonNull(this.protocolHandler);
        this.messageQueue = new MessageQueue(connector, connector.getFramer(), new HexConverter(), handler, scheduler);
        connector.setMessages(this.messageQueue);
        this.bypassStateWorker = new BypassStateWorker(handler, scheduler, this::isConnected);
        this.connectionManager = new ConnectionManagerImpl(this::connect,
                () -> updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Gateway connection lost: Keep-alive timeout"),
                () -> updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Gateway connection lost: Communication error"),
                handler);
        this.channelManager = new ChannelManagerImpl(handler, getThing(), this::isLinked, bypassStateWorker,
                this::isConnected, this::updateState);
        this.sensorManager = handler.getSensorManager();

        scheduler.submit(this::connect);
    }

    /**
     * Attempt to connect and authenticate with the gateway.
     */
    private void connect() {
        ComfoConnectConnector connector = this.connector;
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
            SensorManagerImpl sensorMgr = this.sensorManager;
            if (sensorMgr != null) {
                protocolHandler.setSensorCallback(sensorMgr::handleSensorData);
            }

            // Register keep-alive failure callback
            ConnectionManager connManager = this.connectionManager;
            if (connManager != null) {
                protocolHandler.setKeepAliveFailureCallback(connManager::handleKeepAliveFailure);
            }

            // Register connection error callback for automatic reconnection
            if (connManager != null) {
                protocolHandler.setConnectionErrorCallback(connManager::handleConnectionError);
            }

            // Start the message consumer loop BEFORE protocol initialization
            // so responses can be received and processed
            MessageQueue msgs = this.messageQueue;
            if (msgs != null) {
                msgs.startMessageConsumer();
            }

            protocolHandler.initialize();
            logger.info("ComfoConnect bridge connected and authenticated");

            // Subscribe to sensors based on linked channels
            ChannelManager channelMgr = this.channelManager;
            if (channelMgr != null) {
                channelMgr.subscribeToLinkedChannels();
            }

            updateStatus(ThingStatus.ONLINE);

            ConnectionManager manager = this.connectionManager;
            if (manager != null) {
                manager.cancelReconnectAttempt();
            }
        } catch (InterruptedException e) {
            logger.warn("Connection attempt interrupted: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection interrupted");
            ConnectionManager manager = this.connectionManager;
            if (manager != null) {
                manager.scheduleReconnectAttempt();
            }
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Connection timeout: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection timeout");
            ConnectionManager manager = this.connectionManager;
            if (manager != null) {
                manager.scheduleReconnectAttempt();
            }
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
            ConnectionManager manager = this.connectionManager;
            if (manager != null) {
                manager.scheduleReconnectAttempt();
            }
        }
    }

    @Override
    public void dispose() {
        logger.info("Disposing ComfoConnect bridge: {}", getThing().getUID());

        // Unregister sensor callback
        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;

        if (protocolHandler != null) {
            protocolHandler.setSensorCallback(null);
        }

        ConnectionManager manager = this.connectionManager;
        if (manager != null) {
            manager.cancelReconnectAttempt();
        }

        MessageQueue msgs = this.messageQueue;
        if (msgs != null) {
            msgs.stopMessageConsumer();
        }

        if (protocolHandler != null) {
            protocolHandler.shutdown();
        }

        ComfoConnectConnector connector = this.connector;

        if (connector != null) {
            connector.disconnect();
        }

        // Stop bypass state polling
        BypassStateWorker bypassMgr = this.bypassStateWorker;
        if (bypassMgr != null) {
            bypassMgr.stopBypassStatePolling();
        }

        // Clear subscribed sensors in channel manager
        ChannelManager channelMgr = this.channelManager;
        if (channelMgr != null) {
            channelMgr.clearSubscriptions();
        }

        // Clear subscribed sensors in sensor manager
        SensorManagerImpl sensorMgr = this.sensorManager;
        if (sensorMgr != null) {
            sensorMgr.clearSubscriptions();
        }

        this.connector = null;
        this.protocolHandler = null;
        this.sensorManager = null;
    }

    /**
     * Check if the bridge is currently connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        ComfoConnectConnector connector = this.connector;
        return connector != null && connector.isConnected();
    }

    /**
     * Called when a channel is linked to an item.
     * Subscribes to the corresponding sensor if not already subscribed.
     *
     * @param channelUID the UID of the linked channel
     */
    @Override
    public void channelLinked(ChannelUID channelUID) {
        ChannelManager manager = this.channelManager;
        if (manager != null) {
            manager.channelLinked(channelUID);
        }
    }

    /**
     * Called when a channel is unlinked from an item.
     *
     * @param channelUID the UID of the unlinked channel
     */
    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        ChannelManager manager = this.channelManager;
        if (manager != null) {
            manager.channelUnlinked(channelUID);
        }
    }
}
