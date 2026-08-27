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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages connection lifecycle and reconnection for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ConnectionManagerImpl implements ConnectionManager {

    private final Logger logger = LoggerFactory.getLogger(ConnectionManagerImpl.class);

    private static final int CONNECTION_ATTEMPT_DELAY_SEC = 5;

    private @Nullable ScheduledExecutorService scheduler;
    private final Runnable connectRunnable;
    private final Runnable updateStatusOfflineKeepAliveRunnable;
    private final Runnable updateStatusOfflineConnectionErrorRunnable;
    private @Nullable ComfoConnectProtocolHandler protocolHandler;

    private @Nullable ScheduledFuture<?> connectionRetryTask;

    /**
     * Create a new connection manager.
     *
     * @param connectRunnable the runnable to execute for connection
     * @param updateStatusOfflineKeepAliveRunnable runnable to update status for keep-alive failure
     * @param updateStatusOfflineConnectionErrorRunnable runnable to update status for connection error
     * @param protocolHandler the protocol handler (may be null initially)
     */
    public ConnectionManagerImpl(final Runnable connectRunnable, final Runnable updateStatusOfflineKeepAliveRunnable,
            final Runnable updateStatusOfflineConnectionErrorRunnable,
            final @Nullable ComfoConnectProtocolHandler protocolHandler) {
        this.connectRunnable = connectRunnable;
        this.updateStatusOfflineKeepAliveRunnable = updateStatusOfflineKeepAliveRunnable;
        this.updateStatusOfflineConnectionErrorRunnable = updateStatusOfflineConnectionErrorRunnable;
        this.protocolHandler = protocolHandler;
    }

    @Override
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void connect() {
        // This method is not used in the current implementation
        // The connection is handled by the connectRunnable
    }

    @Override
    public void scheduleReconnectAttempt() {
        ScheduledFuture<?> task = connectionRetryTask;

        if (task != null && !task.isDone()) {
            return; // Retry already scheduled
        }

        logger.debug("Scheduling reconnection attempt in {} seconds", CONNECTION_ATTEMPT_DELAY_SEC);
        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            connectionRetryTask = sched.schedule(connectRunnable, CONNECTION_ATTEMPT_DELAY_SEC, TimeUnit.SECONDS);
        }
    }

    @Override
    public void cancelReconnectAttempt() {
        ScheduledFuture<?> task = connectionRetryTask;
        if (task != null) {
            task.cancel(true);
            connectionRetryTask = null;
        }
    }

    /**
     * Handle keep-alive failure by marking bridge offline and scheduling a fresh reconnection.
     */
    public void handleKeepAliveFailure() {
        logger.warn("Keep-alive timeout detected, attempting fresh connection");
        ComfoConnectProtocolHandler handler = protocolHandler;

        if (handler != null) {
            handler.stopKeepAliveTimer();
        }

        updateStatusOfflineKeepAliveRunnable.run();
        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            sched.schedule(connectRunnable, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Handle connection errors by marking bridge offline and scheduling a fresh reconnection.
     */
    public void handleConnectionError() {
        logger.warn("Connection error detected, attempting fresh connection");
        updateStatusOfflineConnectionErrorRunnable.run();
        ScheduledExecutorService sched = scheduler;
        if (sched != null) {
            sched.schedule(connectRunnable, 5, TimeUnit.SECONDS);
        }
    }
}
