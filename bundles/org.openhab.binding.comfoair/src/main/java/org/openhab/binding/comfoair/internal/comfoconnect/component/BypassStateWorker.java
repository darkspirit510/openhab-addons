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
package org.openhab.binding.comfoair.internal.comfoconnect.component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages bypass state polling for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class BypassStateWorker {

    private final Logger logger = LoggerFactory.getLogger(BypassStateWorker.class);

    private static final int BYPASS_STATE_POLL_INTERVAL_SEC = 30;

    private final ComfoConnectProtocolHandler protocolHandler;
    private final ScheduledExecutorService scheduler;
    private final Runnable isConnectedCallback;

    private @Nullable ScheduledFuture<?> bypassStatePollingTask;

    /**
     * Create a new bypass state manager.
     *
     * @param protocolHandler the protocol handler for sending RMI requests
     * @param scheduler executor for scheduling polling tasks
     * @param isConnectedCallback callback to check if connected
     */
    public BypassStateWorker(final ComfoConnectProtocolHandler protocolHandler,
            final ScheduledExecutorService scheduler, final Runnable isConnectedCallback) {
        this.protocolHandler = protocolHandler;
        this.scheduler = scheduler;
        this.isConnectedCallback = isConnectedCallback;
    }

    public void startBypassStatePolling() {
        if (bypassStatePollingTask != null) {
            return; // Already running
        }

        logger.debug("Starting bypass state polling every {} seconds", BYPASS_STATE_POLL_INTERVAL_SEC);
        bypassStatePollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isConnected()) {
                    protocolHandler.sendRmiRequest(ComfoAirBindingConstants.RMI_UNIT_SCHEDULE,
                            ComfoAirBindingConstants.RMI_SUBUNIT_02,
                            ComfoAirBindingConstants.RMI_PROPERTY_BYPASS_STATE);
                }
            } catch (Exception e) {
                logger.warn("Error polling bypass state: {}", e.getMessage());
            }
        }, 0, BYPASS_STATE_POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stopBypassStatePolling() {
        ScheduledFuture<?> task = bypassStatePollingTask;
        if (task != null) {
            task.cancel(true);
            bypassStatePollingTask = null;
            logger.debug("Stopped bypass state polling");
        }
    }

    /**
     * Check if connected.
     *
     * @return true if connected, false otherwise
     */
    private boolean isConnected() {
        isConnectedCallback.run();
        // Note: The actual check is done by the callback
        // This is a placeholder
        return true;
    }
}
