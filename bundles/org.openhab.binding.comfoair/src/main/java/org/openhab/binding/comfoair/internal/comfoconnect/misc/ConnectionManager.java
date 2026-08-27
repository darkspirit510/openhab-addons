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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface for managing connection lifecycle and reconnection for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface ConnectionManager {

    /**
     * Connect to the gateway.
     */
    void connect();

    /**
     * Schedule a reconnection attempt.
     */
    void scheduleReconnectAttempt();

    /**
     * Cancel any pending reconnection attempt.
     */
    void cancelReconnectAttempt();

    /**
     * Set the executor service for scheduling reconnection attempts.
     *
     * @param scheduler the scheduler to use
     */
    void setScheduler(ScheduledExecutorService scheduler);

    /**
     * Handle keep-alive failure by marking bridge offline and scheduling a fresh reconnection.
     */
    void handleKeepAliveFailure();

    /**
     * Handle connection errors by marking bridge offline and scheduling a fresh reconnection.
     */
    void handleConnectionError();
}
