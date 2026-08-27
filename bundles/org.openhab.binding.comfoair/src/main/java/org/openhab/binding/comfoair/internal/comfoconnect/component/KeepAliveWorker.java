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

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;
import com.zehnder.proto.Zehnder.GatewayOperation.OperationType;

/**
 * Manages keep-alive timing and failure detection for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class KeepAliveWorker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final long KEEPALIVE_INTERVAL_SEC = 30;

    private final ComfoConnectConnector connector;
    private final ScheduledExecutorService scheduler;
    private final ComfoConnectProtocolHandler protocolHandler;
    private final Runnable onFailureCallback;

    private @Nullable ScheduledFuture<?> keepAliveTask;

    /**
     * Create a new keep-alive manager.
     *
     * @param connector the underlying TCP connector
     * @param scheduler executor for scheduling keep-alive tasks
     * @param protocolHandler the protocol handler for allocating references
     * @param onFailureCallback callback to invoke when keep-alive fails
     */
    public KeepAliveWorker(final ComfoConnectConnector connector, final ScheduledExecutorService scheduler,
            final ComfoConnectProtocolHandler protocolHandler, final Runnable onFailureCallback) {
        this.connector = connector;
        this.scheduler = scheduler;
        this.protocolHandler = protocolHandler;
        this.onFailureCallback = onFailureCallback;
    }

    public void startKeepAliveTimer() {
        logger.debug("Starting keep-alive timer");

        keepAliveTask = scheduler.scheduleAtFixedRate(this::sendKeepAlive, KEEPALIVE_INTERVAL_SEC,
                KEEPALIVE_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stopKeepAliveTimer() {
        ScheduledFuture<?> task = keepAliveTask;
        if (task != null) {
            task.cancel(false);
            keepAliveTask = null;
        }
    }

    /**
     * Send a keep-alive message.
     */
    private void sendKeepAlive() {
        try {
            int reference = protocolHandler.allocateReference();
            Zehnder.KeepAlive.Builder builder = Zehnder.KeepAlive.newBuilder();
            byte[] frame = connector.getFramer().createReferencedFrame(OperationType.KeepAliveType, reference,
                    builder.build());
            connector.sendMessage(frame);
            logger.trace("Sent keep-alive message (reference {})", reference);
        } catch (IOException e) {
            logger.warn("Keep-alive failed: {}", e.getMessage());
            onFailureCallback.run();
        }
    }
}
