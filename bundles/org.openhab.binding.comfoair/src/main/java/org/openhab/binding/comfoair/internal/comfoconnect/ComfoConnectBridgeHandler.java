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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge handler for ComfoConnect TCP gateway devices (newer Q-series).
 *
 * Manages:
 * - TCP connection to the gateway
 * - Protocol authentication and session management
 * - Child device discovery and communication
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

        if (config.port <= 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway port is not configured");
            return;
        }

        if (config.pin == null || config.pin.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Gateway PIN is not configured");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        String hostname = Objects.requireNonNull(config.hostname);
        String pin = Objects.requireNonNull(config.pin);

        UUID clientUuid = UUID.nameUUIDFromBytes(("comfoair-client-" + getThing().getUID().toString()).getBytes());
        UUID gatewayUuid = UUID.nameUUIDFromBytes(("comfoair-gateway-" + hostname + ":" + config.port).getBytes());

        ComfoConnectTcpConnector connector = new ComfoConnectTcpConnector(hostname, config.port, clientUuid,
                gatewayUuid);
        this.connector = connector;

        int pinCode;
        try {
            pinCode = Integer.parseInt(pin);
        } catch (NumberFormatException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Gateway PIN must be a numeric value");
            return;
        }

        ComfoConnectProtocolHandler protocolHandler = new ComfoConnectProtocolHandler(connector, pinCode, scheduler);
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

            protocolHandler.initialize();
            logger.info("ComfoConnect bridge connected and authenticated");
            updateStatus(ThingStatus.ONLINE);

            ScheduledFuture<?> task = connectionRetryTask;

            if (task != null) {
                task.cancel(true);
                connectionRetryTask = null;
            }
        } catch (InterruptedException | java.util.concurrent.TimeoutException | IOException e) {
            logger.warn("Failed to connect or authenticate with gateway: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
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

    @Override
    public void dispose() {
        logger.info("Disposing ComfoConnect bridge: {}", getThing().getUID());

        ScheduledFuture<?> task = connectionRetryTask;
        if (task != null) {
            task.cancel(true);
            connectionRetryTask = null;
        }

        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;
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
