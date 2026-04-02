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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thing handler for ComfoConnect devices (individual nodes discovered from gateway).
 *
 * Handles:
 * - Communication with ComfoConnect devices via the bridge protocol handler
 * - Channel state updates from device sensors
 * - Command routing to device methods
 * - Device property management
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectDeviceHandler extends BaseThingHandler {

    private static final int DEFAULT_REFRESH_INTERVAL_SEC = 30;

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectDeviceHandler.class);

    private @Nullable ScheduledFuture<?> poller;
    private @Nullable ComfoConnectProtocolHandler protocolHandler;

    /**
     * Create a new ComfoConnect device handler.
     *
     * @param thing the thing (device)
     */
    public ComfoConnectDeviceHandler(final Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;

        if (protocolHandler == null) {
            logger.warn("Device handler not properly initialized (no protocol handler)");
            return;
        }

        if (command instanceof RefreshType) {
            // Refresh command: force update of this channel
            scheduler.submit(() -> refreshChannelState(channelUID));
        } else {
            // Handle specific command based on channel
            logger.debug("Device {} received command on {}: {}", getThing().getUID(), channelUID, command);

            // TODO: Implement device-specific command handling
            // This would route to appropriate RPC calls on the device
        }
    }

    @Override
    public void initialize() {
        logger.info("Initializing ComfoConnect device: {}", getThing().getUID());

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "No bridge configured for this device");
            return;
        }

        BridgeHandler bridgeHandler = bridge.getHandler();
        if (!(bridgeHandler instanceof ComfoConnectBridgeHandler)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Bridge is not a ComfoConnect bridge");
            return;
        }

        ComfoConnectBridgeHandler comfoConnectBridge = (ComfoConnectBridgeHandler) bridgeHandler;

        if (!comfoConnectBridge.isConnected()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "Bridge is not connected");
            return;
        }

        this.protocolHandler = comfoConnectBridge.getProtocolHandler();

        if (this.protocolHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Protocol handler not available from bridge");
            return;
        }

        updateStatus(ThingStatus.ONLINE);

        // Start polling for device state updates
        startPolling();
    }

    /**
     * Start periodic polling of device state.
     */
    private void startPolling() {
        ComfoConnectDeviceConfiguration config = getConfigAs(ComfoConnectDeviceConfiguration.class);
        int refreshInterval = (config.refreshInterval > 0) ? config.refreshInterval : DEFAULT_REFRESH_INTERVAL_SEC;

        logger.debug("Starting device polling every {} seconds", refreshInterval);

        poller = scheduler.scheduleWithFixedDelay(() -> {
            // TODO: Implement periodic device state updates
            // This would query device sensors and update channel states
        }, 0, refreshInterval, TimeUnit.SECONDS);
    }

    /**
     * Refresh the state of a specific channel.
     *
     * @param channelUID the channel to refresh
     */
    private void refreshChannelState(ChannelUID channelUID) {
        logger.debug("Refreshing state for channel: {}", channelUID);

        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;
        if (protocolHandler == null) {
            logger.warn("Protocol handler not available for refresh");
            return;
        }

        // TODO: Implement channel-specific refresh logic
        // This would query the specific property from the device via the protocol handler
    }

    @Override
    public void dispose() {
        logger.info("Disposing ComfoConnect device: {}", getThing().getUID());

        // Stop polling
        ScheduledFuture<?> poller = this.poller;
        if (poller != null) {
            poller.cancel(true);
            this.poller = null;
        }

        this.protocolHandler = null;
    }

    @Override
    public void bridgeStatusChanged(org.openhab.core.thing.ThingStatusInfo bridgeStatusInfo) {
        logger.debug("Bridge status changed to: {}", bridgeStatusInfo.getStatus());

        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            // Bridge came online, try to come online too
            scheduler.submit(this::initialize);
        } else {
            // Bridge went offline, take device offline
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
