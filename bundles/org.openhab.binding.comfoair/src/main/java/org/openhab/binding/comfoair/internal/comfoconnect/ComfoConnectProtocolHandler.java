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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.PendingRequest;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.SensorDataCallback;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.openhab.binding.comfoair.internal.comfoconnect.usecase.ListRegisteredAppsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.MessageLite;
import com.zehnder.proto.Zehnder;
import com.zehnder.proto.Zehnder.GatewayOperation;
import com.zehnder.proto.Zehnder.GatewayOperation.GatewayResult;

/**
 * Handles ComfoConnect protocol state machine: authentication, session management, and message correlation.
 *
 * Responsibilities:
 * - Registration with PIN
 * - Session management (start/close)
 * - Outgoing request tracking with reference-based correlation
 * - KeepAlive supervision (30-60 second intervals)
 * - Timeout handling for pending requests
 * - Sensor subscription management using centralized sensor registry
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectProtocolHandler {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectProtocolHandler.class);

    private static final long KEEPALIVE_INTERVAL_SEC = 30;
    private static final long REQUEST_TIMEOUT_SEC = 5;
    private static final int REQUEST_RETRY_COUNT = 3;
    private static final long REQUEST_RETRY_DELAY_SEC = 5;
    private static final int MAX_REFERENCE = 0xFFFFFF; // 24-bit reference

    private final ComfoConnectConnector connector;
    private final ScheduledExecutorService scheduler;
    private final int pinCode;
    private final boolean autoTakeover;

    private @Nullable SensorDataCallback sensorCallback;
    private @Nullable Runnable onKeepAliveFailure;

    private volatile boolean sessionActive = false;
    private volatile int nextReference = 1;
    private final Map<Integer, PendingRequest<?>> pendingRequests = new HashMap<>();
    private @Nullable ScheduledFuture<?> keepAliveTask;

    /**
     * Create a new protocol handler.
     *
     * @param connector the underlying TCP connector
     * @param pinCode the PIN for gateway registration
     * @param autoTakeover whether to automatically take over existing sessions
     * @param scheduler executor for async tasks and keep-alive
     */
    public ComfoConnectProtocolHandler(final ComfoConnectConnector connector, final int pinCode,
            final boolean autoTakeover, final ScheduledExecutorService scheduler) {
        this.connector = connector;
        this.pinCode = pinCode;
        this.autoTakeover = autoTakeover;
        this.scheduler = scheduler;
    }

    /**
     * Set the sensor data callback for receiving sensor updates.
     *
     * @param callback the callback to invoke when sensor data arrives, or null to unregister
     */
    public void setSensorCallback(final @Nullable SensorDataCallback callback) {
        this.sensorCallback = callback;
    }

    /**
     * Initialize the protocol: register (if needed) and start session.
     * This must be called after the TCP connection is established.
     *
     * @throws IOException if registration or session start fails
     * @throws InterruptedException if the operation is interrupted
     * @throws TimeoutException if the operation times out
     */
    public void initialize() throws IOException, InterruptedException, TimeoutException {
        logger.debug("Initializing ComfoConnect protocol");

        registerAppIfNeeded();
        startSession();
        startKeepAliveTimer();

        // Note: Sensor subscriptions are now handled by the ComfoConnectHandler.
        // It will subscribe to sensors based on which channels are linked.
        // See ComfoConnectHandler.subscribeToLinkedChannels()

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
     * Send a request with automatic retry on timeout.
     *
     * @param request the request message
     * @param responseClass the expected response class
     * @param timeoutSec timeout in seconds for each attempt
     * @return the response
     * @throws IOException if all retries fail
     * @throws InterruptedException if interrupted
     */
    private <T> T sendRequestWithRetry(final com.google.protobuf.MessageLite request, final Class<T> responseClass,
            final long timeoutSec) throws IOException, InterruptedException {
        int attempts = 0;
        IOException lastException = null;

        while (attempts < REQUEST_RETRY_COUNT) {
            attempts++;

            try {
                return sendRequestSync(request, responseClass, timeoutSec);
            } catch (TimeoutException e) {
                lastException = new IOException("Timeout after attempt " + attempts, e);
                logger.debug("Request attempt {} timed out, retrying in {} seconds", attempts, REQUEST_RETRY_DELAY_SEC);

                if (attempts < REQUEST_RETRY_COUNT) {
                    Thread.sleep(REQUEST_RETRY_DELAY_SEC * 1000);
                }
            }
        }

        throw lastException != null ? lastException
                : new IOException("Request failed after " + REQUEST_RETRY_COUNT + " attempts");
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

            logger.info("Sending {} (reference {})", request.getClass().getSimpleName(), reference);
            byte[] frame = connector.getFramer().createFrame(opBuilder.build(), request);
            connector.sendMessage(frame);

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

        logger.info("Received message: {} bytes, first byte: 0x{}", frame.length, String.format("%02X", frame[0]));

        try {
            ProtobufFramer.ParsedFrame parsed = connector.getFramer().parseFrame(frame);

            if (parsed == null) {
                logger.warn("Failed to parse frame");
                return;
            }

            GatewayOperation operation = GatewayOperation.parseFrom(parsed.command);
            logger.info("Received operation: type={}, reference={}, result={}", operation.getType(),
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
                case ListRegisteredAppsConfirmType:
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), parsed.payload, operation.getResult());
                    }
                    break;

                case CnNodeNotificationType:
                case CnRpdoNotificationType:
                case CnAlarmNotificationType:
                    logger.info("Handling notification: type={}, payload length={}", operation.getType(),
                            parsed.payload.length);
                    handleNotification(operation, parsed.payload);
                    break;

                default:
                    // Other async responses (CnRmiAsyncResponse, etc.)
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), parsed.payload, operation.getResult());
                    }

                    break;
            }

        } catch (Exception e) {
            logger.error("Error handling incoming message: {}", e.getMessage(), e);
        }
    }

    /**
     * Register the app if not already registered. Attempts registration and ignores error if already
     * registered.
     *
     * @throws IOException if registration fails
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private void registerAppIfNeeded() throws IOException, TimeoutException, InterruptedException {
        logger.debug("Checking if app registration is needed");

        try {
            // Get list of already registered apps
            ListRegisteredAppsUseCase useCase = new ListRegisteredAppsUseCase(this::sendRequestWithRetryWrapper);
            List<UUID> registeredApps = useCase.execute();
            UUID clientUuid = connector.getClientUuid();

            if (registeredApps.contains(clientUuid)) {
                logger.debug("App already registered with UUID: {}", clientUuid);
                return;
            }

            logger.debug("App not yet registered, attempting registration");
            registerApp();
        } catch (IOException e) {
            // If listing registered apps fails, attempt registration as fallback
            logger.debug("Failed to list registered apps ({}), attempting registration anyway", e.getMessage());
            registerApp(); // Will throw IOException if fails, causing initialization to fail
        }
    }

    /**
     * Wrapper method for sendRequestWithRetry that matches the RequestExecutor interface.
     *
     * @param request the request message
     * @return the response as byte array
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted during execution
     */
    private byte[] sendRequestWithRetryWrapper(com.google.protobuf.MessageLite request)
            throws IOException, InterruptedException {
        return sendRequestWithRetry(request, byte[].class, REQUEST_TIMEOUT_SEC);
    }

    /**
     * Register the app with the gateway using PIN.
     *
     * @throws IOException if registration fails (e.g., invalid PIN)
     * @throws InterruptedException if interrupted
     */
    private void registerApp() throws IOException, InterruptedException {
        logger.debug("Registering app with gateway");

        Zehnder.RegisterAppRequest.Builder builder = Zehnder.RegisterAppRequest.newBuilder();
        builder.setUuid(com.google.protobuf.ByteString.copyFrom(uuidToBytes(connector.getClientUuid())));
        builder.setPin(pinCode);
        builder.setDevicename("openHAB");

        // sendRequestWithRetry will retry up to 3 times with 5-second delays
        // If gateway returns NOT_ALLOWED result, it will be caught as an error
        sendRequestWithRetry(builder.build(), byte[].class, REQUEST_TIMEOUT_SEC);
        logger.info("App registered successfully with UUID: {}", connector.getClientUuid());
    }

    /**
     * Start a session with the gateway, with optional takeover of existing sessions.
     *
     * @throws IOException if start fails or session conflict exists
     * @throws InterruptedException if interrupted
     */
    private void startSession() throws IOException, InterruptedException {
        logger.debug("Starting session with gateway (autoTakeover={})", autoTakeover);

        Zehnder.StartSessionRequest.Builder builder = Zehnder.StartSessionRequest.newBuilder();
        builder.setTakeover(autoTakeover);

        // sendRequestWithRetry will retry up to 3 times with 5-second delays
        // For OTHER_SESSION result, we either allow it (if autoTakeover=true) or fail
        sendRequestWithRetry(builder.build(), byte[].class, REQUEST_TIMEOUT_SEC);
        logger.info("Session started successfully");
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
     * Set the callback to be invoked when keep-alive fails.
     *
     * @param callback the callback to invoke on keep-alive failure
     */
    public void setKeepAliveFailureCallback(final Runnable callback) {
        this.onKeepAliveFailure = callback;
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
            int reference = allocateReference();
            Zehnder.KeepAlive.Builder builder = Zehnder.KeepAlive.newBuilder();
            byte[] frame = connector.getFramer().createFrame(GatewayOperation.newBuilder()
                    .setType(GatewayOperation.OperationType.KeepAliveType).setReference(reference).build(),
                    builder.build());
            connector.sendMessage(frame);
            logger.trace("Sent keep-alive message (reference {})", reference);
        } catch (IOException e) {
            logger.warn("Keep-alive failed: {}", e.getMessage());
            Runnable callback = onKeepAliveFailure;
            if (callback != null) {
                callback.run();
            }
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
     * @param result the gateway result from the operation
     */
    private void completeRequest(final int reference, final byte[] payload, final GatewayResult result) {
        PendingRequest<?> pending;

        synchronized (pendingRequests) {
            pending = pendingRequests.remove(reference);
        }

        if (pending == null) {
            logger.trace("Received response for unknown reference: {}", reference);
            return;
        }

        try {
            // Check for gateway errors first
            if (result != GatewayResult.OK) {
                String errorMsg = getErrorMessageForResult(result);
                pending.future.completeExceptionally(new IOException(errorMsg));
                return;
            }

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
     * Get a human-readable error message for a gateway result.
     *
     * @param result the gateway result
     * @return error message
     */
    private String getErrorMessageForResult(final GatewayResult result) {
        return switch (result) {
            case OK -> "No error";
            case BAD_REQUEST -> "Gateway error: BAD_REQUEST (something wrong with the request)";
            case INTERNAL_ERROR -> "Gateway error: INTERNAL_ERROR (request was OK but handling failed)";
            case NOT_REACHABLE -> "Gateway error: NOT_REACHABLE (backend cannot route the request)";
            case OTHER_SESSION -> "Gateway error: OTHER_SESSION (another session already active)";
            case NOT_ALLOWED -> "Gateway error: NOT_ALLOWED (invalid PIN or permission denied)";
            case NO_RESOURCES -> "Gateway error: NO_RESOURCES (not enough memory)";
            case NOT_EXIST -> "Gateway error: NOT_EXIST (ComfoNet node or property does not exist)";
            case RMI_ERROR -> "Gateway error: RMI_ERROR (RMI communication failed)";
        };
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
        logger.info("Received notification: type={}, payload length={}", operation.getType(), payload.length);

        if (operation.getType() == GatewayOperation.OperationType.CnRpdoNotificationType) {
            logger.info("Processing CnRpdoNotification with {} bytes", payload.length);
            handleRpdoNotification(payload);
        } else {
            logger.info("Ignoring notification type: {}", operation.getType());
        }
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
    private void setOperationType(final GatewayOperation.Builder builder, final MessageLite request) {
        String requestType = request.getClass().getSimpleName();

        if (requestType.contains("RegisterApp")) {
            builder.setType(GatewayOperation.OperationType.RegisterAppRequestType);
        } else if (requestType.contains("StartSession")) {
            builder.setType(GatewayOperation.OperationType.StartSessionRequestType);
        } else if (requestType.contains("ListRegisteredApps")) {
            builder.setType(GatewayOperation.OperationType.ListRegisteredAppsRequestType);
        } else if (requestType.contains("KeepAlive")) {
            builder.setType(GatewayOperation.OperationType.KeepAliveType);
        }
        // Add other types as needed
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    /**
     * Subscribe to a sensor.
     * This is the generic method that replaces all individual subscribeToXxxSensor methods.
     *
     * @param sensor the sensor to subscribe to
     * @param sensorType the sensor data type (from SensorValueType)
     */
    public void subscribeToSensor(final Sensor sensor, final SensorValueType sensorType) {
        try {
            logger.debug("Subscribing to sensor {} (PDO {} type {})", sensor, sensor.id, sensorType.value);
            connector.sendRpdoRequest(sensor.id, sensorType.value);
            logger.debug("Sensor {} subscription request sent successfully", sensor);
        } catch (IOException e) {
            logger.warn("Failed to subscribe to sensor {}: {}", sensor, e.getMessage());
        }
    }

    /**
     * Handle incoming RPDO notification from the gateway.
     *
     * @param payload the RPDO notification payload
     */
    private void handleRpdoNotification(final byte[] payload) {
        try {
            logger.debug("handleRpdoNotification: payload length={}", payload.length);

            if (payload.length < 4) {
                logger.warn("Invalid RPDO notification: payload too short (length={})", payload.length);
                return;
            }

            // Extract sensor ID (protobuf format: byte 0 is field tag, byte 1 is the value)
            // For PDO sensor ID, we expect: byte 0 = 0x08 (field 1, varint), byte 1 = sensor ID
            int sensorId = payload[1] & 0xFF;

            // Get sensor object for better logging
            Sensor sensor = Sensors.findById(sensorId).orElse(null);

            if (sensor == null) {
                logger.warn("Received notification for unknown sensor with ID {}, ignoring it", sensorId);
                return;
            }

            logger.debug("RPDO notification for sensor: {}", sensor);

            // Route to appropriate handler based on sensor ID
            // Extract sensor data based on sensor type
            SensorDataCallback callback = sensorCallback;

            int sensorValue = sensor.parseValueFrom(payload);
            logger.debug("Invoking callback with sensor={}, value={}", sensor, sensorValue);

            if (callback != null) {
                callback.onSensorDataReceived(sensor, sensorValue);
            }
        } catch (Exception e) {
            logger.error("Error handling RPDO notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert UUID to bytes.
     *
     * @param uuid the UUID
     * @return 16 bytes
     */
    private byte[] uuidToBytes(final java.util.UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bytes;
    }

    /**
     * Convert bytes to UUID.
     *
     * @param bytes the 16 bytes
     * @return UUID object
     */
    private java.util.UUID bytesToUuid(final byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();

        return new java.util.UUID(high, low);
    }
}
