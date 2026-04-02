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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;
import com.zehnder.proto.Zehnder.GatewayOperation;

/**
 * Handles ComfoConnect protocol state machine: authentication, session management, and message correlation.
 *
 * Responsibilities:
 * - Registration with PIN
 * - Session management (start/close)
 * - Outgoing request tracking with reference-based correlation
 * - KeepAlive supervision (30-60 second intervals)
 * - Timeout handling for pending requests
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectProtocolHandler {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectProtocolHandler.class);

    private static final long KEEPALIVE_INTERVAL_SEC = 30;
    private static final long REQUEST_TIMEOUT_SEC = 10;
    private static final int MAX_REFERENCE = 0xFFFFFF; // 24-bit reference

    private final ComfoConnectConnector connector;
    private final ScheduledExecutorService scheduler;
    private final int pinCode;

    private volatile boolean sessionActive = false;
    private volatile int nextReference = 1;
    private final Map<Integer, PendingRequest<?>> pendingRequests = new HashMap<>();
    private @Nullable ScheduledFuture<?> keepAliveTask;

    /**
     * Container for pending request information.
     */
    public static class PendingRequest<T> {
        public final int reference;
        public final long createdTime;
        public final CompletableFuture<T> future;
        public final Class<T> responseClass;

        public PendingRequest(final int reference, final CompletableFuture<T> future, final Class<T> responseClass) {
            this.reference = reference;
            this.future = future;
            this.responseClass = responseClass;
            this.createdTime = System.currentTimeMillis();
        }

        public boolean isExpired(final long timeoutMs) {
            return (System.currentTimeMillis() - createdTime) > timeoutMs;
        }
    }

    /**
     * Create a new protocol handler.
     *
     * @param connector the underlying TCP connector
     * @param pinCode the PIN for gateway registration
     * @param scheduler executor for async tasks and keep-alive
     */
    public ComfoConnectProtocolHandler(final ComfoConnectConnector connector, final int pinCode,
            final ScheduledExecutorService scheduler) {
        this.connector = connector;
        this.pinCode = pinCode;
        this.scheduler = scheduler;
    }

    /**
     * Initialize the protocol: register and start session.
     * This must be called after the TCP connection is established.
     *
     * @throws IOException if registration or session start fails
     * @throws InterruptedException if the operation is interrupted
     * @throws TimeoutException if the operation times out
     */
    public void initialize() throws IOException, InterruptedException, TimeoutException {
        logger.debug("Initializing ComfoConnect protocol");

        // Register app with PIN
        registerApp();

        // Start session
        startSession();

        // Start keep-alive timer
        startKeepAliveTimer();

        sessionActive = true;
        logger.info("ComfoConnect protocol initialized successfully");
    }

    /**
     * Shutdown the protocol handler gracefully.
     * This closes the session and stops keep-alive.
     */
    public void shutdown() {
        logger.debug("Shutting down ComfoConnect protocol");
        stopKeepAliveTimer();

        try {
            if (sessionActive) {
                closeSession();
            }
        } catch (IOException e) {
            logger.debug("Error closing session: {}", e.getMessage());
        }

        sessionActive = false;
        clearPendingRequests();
    }

    /**
     * Send a request and wait for response (blocking with timeout).
     *
     * @param request the request message
     * @param responseClass the expected response class
     * @param timeoutSec timeout in seconds
     * @return the response message
     * @throws IOException if send fails
     * @throws TimeoutException if response times out
     * @throws InterruptedException if interrupted while waiting
     */
    public <T> T sendRequestSync(final com.google.protobuf.MessageLite request, final Class<T> responseClass,
            final long timeoutSec) throws IOException, TimeoutException, InterruptedException {
        CompletableFuture<T> future = sendRequestAsync(request, responseClass);
        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Request failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Send a request asynchronously.
     *
     * @param request the request message
     * @param responseClass the expected response class
     * @return a future that will contain the response
     * @throws IOException if send fails
     */
    public <T> CompletableFuture<T> sendRequestAsync(final com.google.protobuf.MessageLite request,
            final Class<T> responseClass) throws IOException {
        int reference = allocateReference();
        CompletableFuture<T> future = new CompletableFuture<>();
        PendingRequest<T> pendingRequest = new PendingRequest<>(reference, future, responseClass);

        synchronized (pendingRequests) {
            pendingRequests.put(reference, pendingRequest);
        }

        try {
            GatewayOperation.Builder opBuilder = GatewayOperation.newBuilder();
            opBuilder.setReference(reference);
            setOperationType(opBuilder, request);

            byte[] frame = connector.getFramer().createFrame(opBuilder.build(), request);
            connector.sendMessage(frame);
            logger.trace("Sent request with reference {}", reference);

        } catch (IOException e) {
            synchronized (pendingRequests) {
                pendingRequests.remove(reference);
            }
            future.completeExceptionally(e);
            throw e;
        }

        scheduler.schedule(() -> handleRequestTimeout(reference), REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS);

        return future;
    }

    /**
     * Process incoming message from gateway.
     * Called by the connector when a complete frame arrives.
     *
     * @param frame the complete frame bytes
     */
    public void handleIncomingMessage(final byte @org.eclipse.jdt.annotation.Nullable [] frame) {
        if (frame == null) {
            logger.debug("Received null frame");
            return;
        }

        try {
            ProtobufFramer.ParsedFrame parsed = connector.getFramer().parseFrame(frame);
            if (parsed == null) {
                logger.warn("Failed to parse frame");
                return;
            }

            GatewayOperation operation = GatewayOperation.parseFrom(parsed.command);
            logger.trace("Received operation: type={}, reference={}, result={}", operation.getType(),
                    operation.getReference(), operation.getResult());

            if (operation.getResult() != GatewayOperation.GatewayResult.OK) {
                logger.warn("Gateway returned error: {} - {}", operation.getResult(), operation.getResultDescription());
            }

            switch (operation.getType()) {
                case KeepAliveType:
                    break;

                case RegisterAppConfirmType:
                case StartSessionConfirmType:
                case CloseSessionConfirmType:
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), parsed.payload);
                    }
                    break;

                case CnNodeNotificationType:
                case CnRpdoNotificationType:
                case CnAlarmNotificationType:
                    handleNotification(operation, parsed.payload);
                    break;

                default:
                    // Other async responses (CnRmiAsyncResponse, etc.)
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), parsed.payload);
                    }

                    break;
            }

        } catch (Exception e) {
            logger.error("Error handling incoming message: {}", e.getMessage(), e);
        }
    }

    /**
     * Register the app with the gateway using PIN.
     *
     * @throws IOException if registration fails
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private void registerApp() throws IOException, TimeoutException, InterruptedException {
        logger.debug("Registering app with gateway");

        Zehnder.RegisterAppRequest.Builder builder = Zehnder.RegisterAppRequest.newBuilder();
        builder.setUuid(com.google.protobuf.ByteString.copyFrom(uuidToBytes(connector.getClientUuid())));
        builder.setPin(pinCode);
        builder.setDevicename("openHAB");

        sendRequestSync(builder.build(), byte[].class, REQUEST_TIMEOUT_SEC);
        logger.debug("App registered successfully");
    }

    /**
     * Start a session with the gateway.
     *
     * @throws IOException if start fails
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private void startSession() throws IOException, TimeoutException, InterruptedException {
        logger.debug("Starting session with gateway");

        Zehnder.StartSessionRequest.Builder builder = Zehnder.StartSessionRequest.newBuilder();
        builder.setTakeover(false);

        sendRequestSync(builder.build(), byte[].class, REQUEST_TIMEOUT_SEC);
        logger.debug("Session started successfully");
    }

    /**
     * Close the session with the gateway.
     *
     * @throws IOException if close fails
     */
    private void closeSession() throws IOException {
        logger.debug("Closing session with gateway");

        Zehnder.CloseSessionRequest.Builder builder = Zehnder.CloseSessionRequest.newBuilder();

        try {
            byte[] frame = connector.getFramer().createFrame(GatewayOperation.newBuilder()
                    .setType(GatewayOperation.OperationType.CloseSessionRequestType).build(), builder.build());
            connector.sendMessage(frame);
        } catch (IOException e) {
            logger.debug("Error sending close session: {}", e.getMessage());
        }

        logger.debug("Session closed");
    }

    /**
     * Start the keep-alive timer.
     */
    private void startKeepAliveTimer() {
        logger.debug("Starting keep-alive timer");
        keepAliveTask = scheduler.scheduleAtFixedRate(this::sendKeepAlive, KEEPALIVE_INTERVAL_SEC,
                KEEPALIVE_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /**
     * Stop the keep-alive timer.
     */
    private void stopKeepAliveTimer() {
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
            Zehnder.KeepAlive.Builder builder = Zehnder.KeepAlive.newBuilder();
            byte[] frame = connector.getFramer().createFrame(
                    GatewayOperation.newBuilder().setType(GatewayOperation.OperationType.KeepAliveType).build(),
                    builder.build());
            connector.sendMessage(frame);
            logger.trace("Sent keep-alive message");
        } catch (IOException e) {
            logger.warn("Error sending keep-alive: {}", e.getMessage());
        }
    }

    /**
     * Allocate a unique reference number for a request.
     *
     * @return a unique reference
     */
    private synchronized int allocateReference() {
        int ref = nextReference++;
        if (nextReference > MAX_REFERENCE) {
            nextReference = 1;
        }
        return ref;
    }

    /**
     * Complete a pending request with response data.
     *
     * @param reference the request reference
     * @param payload the response payload bytes
     */
    private void completeRequest(final int reference, final byte[] payload) {
        PendingRequest<?> pending;
        synchronized (pendingRequests) {
            pending = pendingRequests.remove(reference);
        }

        if (pending == null) {
            logger.trace("Received response for unknown reference: {}", reference);
            return;
        }

        try {
            // Parse response based on expected type
            Object response = parseResponse(pending.responseClass, payload);
            @SuppressWarnings("unchecked")
            PendingRequest<Object> typedPending = (PendingRequest<Object>) pending;
            typedPending.future.complete(response);
        } catch (Exception e) {
            pending.future.completeExceptionally(e);
        }
    }

    /**
     * Handle a request timeout.
     *
     * @param reference the request reference that timed out
     */
    private void handleRequestTimeout(final int reference) {
        PendingRequest<?> pending;
        synchronized (pendingRequests) {
            pending = pendingRequests.remove(reference);
        }

        if (pending != null) {
            pending.future.completeExceptionally(
                    new TimeoutException("Request " + reference + " timed out after " + REQUEST_TIMEOUT_SEC + "s"));
            logger.warn("Request {} timed out", reference);
        }
    }

    /**
     * Handle asynchronous notifications from the gateway.
     *
     * @param operation the gateway operation
     * @param payload the payload bytes
     */
    private void handleNotification(final GatewayOperation operation, final byte[] payload) {
        logger.trace("Received notification: type={}", operation.getType());
        // TODO: Route to listeners
    }

    /**
     * Clear all pending requests (on shutdown).
     */
    private void clearPendingRequests() {
        synchronized (pendingRequests) {
            for (PendingRequest<?> pending : pendingRequests.values()) {
                pending.future.completeExceptionally(new IOException("Protocol handler shut down"));
            }
            pendingRequests.clear();
        }
    }

    /**
     * Parse response based on expected type.
     *
     * @param responseClass the expected response class
     * @param payload the payload bytes
     * @return parsed response object
     */
    private Object parseResponse(final Class<?> responseClass, final byte[] payload) throws IOException {
        if (responseClass == byte[].class) {
            return payload;
        }
        throw new IOException("Unknown response type: " + responseClass.getName());
    }

    /**
     * Set the operation type in GatewayOperation based on request type.
     *
     * @param builder the GatewayOperation builder
     * @param request the request message
     */
    private void setOperationType(final GatewayOperation.Builder builder,
            final com.google.protobuf.MessageLite request) {
        String requestType = request.getClass().getSimpleName();
        if (requestType.contains("RegisterApp")) {
            builder.setType(GatewayOperation.OperationType.RegisterAppRequestType);
        } else if (requestType.contains("StartSession")) {
            builder.setType(GatewayOperation.OperationType.StartSessionRequestType);
        } else if (requestType.contains("KeepAlive")) {
            builder.setType(GatewayOperation.OperationType.KeepAliveType);
        }
        // Add other types as needed
    }

    /**
     * Convert UUID to bytes.
     *
     * @param uuid the UUID
     * @return 16 bytes
     */
    private byte[] uuidToBytes(final java.util.UUID uuid) {
        byte[] bytes = new byte[16];
        java.nio.ByteBuffer.wrap(bytes).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bytes;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }
}
