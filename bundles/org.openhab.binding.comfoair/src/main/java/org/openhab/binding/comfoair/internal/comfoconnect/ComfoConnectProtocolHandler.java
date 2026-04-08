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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // Sensor subscription
    private static final int SENSOR_FAN_SPEED_MODE = 65; // PDO sensor 65: fan speed (0-3)
    private static final int SENSOR_FAN_SPEED_TYPE = 1; // TYPE_CN_UINT8

    // Sensor data types
    private static final int TYPE_CN_UINT8 = 1;
    private static final int TYPE_CN_UINT64 = 7; // 64-bit unsigned integer

    // Phase 1: Fan-related sensors (no value corrections)
    private static final int SENSOR_OPERATING_MODE = 1; // TYPE_CN_UINT8
    private static final int SENSOR_SUPPLY_FAN_SPEED = 74; // TYPE_CN_UINT8
    private static final int SENSOR_EXHAUST_FAN_SPEED = 75; // TYPE_CN_UINT8
    private static final int SENSOR_SUPPLY_FAN_SPEED_SET = 76; // TYPE_CN_UINT8
    private static final int SENSOR_EXHAUST_FAN_SPEED_SET = 77; // TYPE_CN_UINT8
    private static final int SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE = 66; // TYPE_CN_UINT8
    private static final int SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE = 67; // TYPE_CN_UINT8
    private static final int SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE_SET = 68; // TYPE_CN_UINT8
    private static final int SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE_SET = 69; // TYPE_CN_UINT8
    private static final int SENSOR_BYPASS_STATE = 81; // TYPE_CN_UINT8
    private static final int SENSOR_PREHEATER_STATE = 82; // TYPE_CN_UINT8
    private static final int SENSOR_CURRENT_HUMIDITY = 10; // TYPE_CN_UINT8
    private static final int SENSOR_TARGET_HUMIDITY = 11; // TYPE_CN_UINT8
    private static final int SENSOR_HUMIDIFIER_HUMIDITY = 209; // TYPE_CN_UINT8

    // Phase 2: Other basic sensors (no value corrections)
    private static final int SENSOR_WEEK_PROFILE_ACTIVE = 12; // TYPE_CN_UINT8
    private static final int SENSOR_GLOBAL_ALLERGEN_MODE = 32; // TYPE_CN_UINT8
    private static final int SENSOR_EWT_SPEED = 88; // TYPE_CN_UINT8
    private static final int SENSOR_EWT_POSITION = 89; // TYPE_CN_UINT8
    private static final int SENSOR_ENTHALPY_STATE = 96; // TYPE_CN_UINT8
    private static final int SENSOR_FROST_PROTECTION_SPEED = 97; // TYPE_CN_UINT8
    private static final int SENSOR_FROST_PROTECTION_LOSS = 98; // TYPE_CN_UINT8
    private static final int SENSOR_FROST_PROTECTION_TIMEOUT = 99; // TYPE_CN_UINT8
    private static final int SENSOR_HCE_PRESENT = 200; // TYPE_CN_UINT8

    // Phase 3: Sensors with value corrections
    // Temperature sensors (divide by 10): 2, 3, 4, 5, 100, 101, 102, 103, 104
    private static final int SENSOR_OUTDOOR_TEMPERATURE_IN = 2; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_OUTDOOR_TEMPERATURE_OUT = 3; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_INDOOR_TEMPERATURE_IN = 4; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_INDOOR_TEMPERATURE_OUT = 5; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_EWT_TEMPERATURE = 100; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_COOKER_TEMPERATURE = 101; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_HEATER_TEMPERATURE = 102; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_PRE_HEATER_TEMPERATURE = 103; // TYPE_CN_UINT8, divide by 10
    private static final int SENSOR_INDOOR_HUMIDITY = 104; // TYPE_CN_UINT8, divide by 10
    // Humidity sensors (no correction): 13, 14, 15, 16, 105
    private static final int SENSOR_EXHAUST_HUMIDITY = 13; // TYPE_CN_UINT8
    private static final int SENSOR_INDOOR_HUMIDITY_2 = 14; // TYPE_CN_UINT8
    private static final int SENSOR_EXHAUST_HUMIDITY_2 = 15; // TYPE_CN_UINT8
    private static final int SENSOR_INDOOR_HUMIDITY_3 = 16; // TYPE_CN_UINT8
    private static final int SENSOR_COMFOSUPPLY_HUMIDITY = 105; // TYPE_CN_UINT8
    // Boolean sensors: 17, 18, 21
    private static final int SENSOR_T1_SENSOR_PRESENT = 17; // TYPE_CN_UINT8
    private static final int SENSOR_T2_SENSOR_PRESENT = 18; // TYPE_CN_UINT8
    private static final int SENSOR_T3_SENSOR_PRESENT = 21; // TYPE_CN_UINT8
    // Mapping sensor: 208 (0→Celsius, else→Fahrenheit)
    private static final int SENSOR_TEMPERATURE_UNIT = 208; // TYPE_CN_UINT8

    // Phase 4: Complex sensors
    private static final int SENSOR_AIRFLOW_CONSTRAINTS = 230; // TYPE_CN_UINT64, requires bit-shifting

    private @Nullable SensorDataCallback sensorCallback;
    private @Nullable Runnable onKeepAliveFailure;

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
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private <T> T sendRequestWithRetry(final com.google.protobuf.MessageLite request, final Class<T> responseClass,
            final long timeoutSec) throws IOException, TimeoutException, InterruptedException {
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
                case ListRegisteredAppsConfirmType:
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), parsed.payload, operation.getResult());
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
            List<String> registeredApps = listRegisteredApps();
            String clientUuid = connector.getClientUuid().toString();

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
     * List all registered app UUIDs from the gateway.
     *
     * @return list of UUID strings
     * @throws IOException if request fails
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private List<String> listRegisteredApps() throws IOException, TimeoutException, InterruptedException {
        logger.debug("Requesting list of registered apps");

        Zehnder.ListRegisteredAppsRequest.Builder builder = Zehnder.ListRegisteredAppsRequest.newBuilder();

        byte[] response = sendRequestWithRetry(builder.build(), byte[].class, REQUEST_TIMEOUT_SEC);

        // Parse the ListRegisteredAppsConfirm response
        List<String> uuids = new ArrayList<>();
        try {
            Zehnder.ListRegisteredAppsConfirm confirm = Zehnder.ListRegisteredAppsConfirm.parseFrom(response);
            for (Zehnder.ListRegisteredAppsConfirm.App app : confirm.getAppsList()) {
                java.util.UUID registeredUuid = bytesToUuid(app.getUuid().toByteArray());
                uuids.add(registeredUuid.toString());
                logger.trace("Found registered app: {} ({})", registeredUuid, app.getDevicename());
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            logger.warn("Failed to parse ListRegisteredAppsConfirm: {}", e.getMessage());
            throw new IOException("Failed to parse registered apps response", e);
        }

        logger.debug("Found {} registered apps", uuids.size());
        return uuids;
    }

    /**
     * Register the app with the gateway using PIN.
     *
     * @throws IOException if registration fails (e.g., invalid PIN)
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private void registerApp() throws IOException, TimeoutException, InterruptedException {
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
     * @throws TimeoutException if timeout occurs
     * @throws InterruptedException if interrupted
     */
    private void startSession() throws IOException, TimeoutException, InterruptedException {
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
        logger.debug("Received notification: type={}, payload length={}", operation.getType(), payload.length);

        if (operation.getType() == GatewayOperation.OperationType.CnRpdoNotificationType) {
            logger.debug("Processing CnRpdoNotification with {} bytes", payload.length);
            handleRpdoNotification(payload);
        } else {
            logger.debug("Ignoring notification type: {}", operation.getType());
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

    public boolean isSessionActive() {
        return sessionActive;
    }

    /**
     * Subscribe to the fan speed sensor (PDO sensor 65).
     */
    public void subscribeToFanSpeedSensor() {
        try {
            logger.debug("Subscribing to fan speed sensor (PDO {} type {})", SENSOR_FAN_SPEED_MODE,
                    SENSOR_FAN_SPEED_TYPE);
            connector.sendRpdoRequest(SENSOR_FAN_SPEED_MODE, SENSOR_FAN_SPEED_TYPE);
            logger.debug("Fan speed sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to fan speed sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to operating mode sensor (PDO 1).
     */
    public void subscribeToOperatingModeSensor() {
        try {
            logger.debug("Subscribing to operating mode sensor (PDO {} type {})", SENSOR_OPERATING_MODE, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_OPERATING_MODE, TYPE_CN_UINT8);
            logger.debug("Operating mode sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to operating mode sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to supply fan speed sensor (PDO 74).
     */
    public void subscribeToSupplyFanSpeedSensor() {
        try {
            logger.debug("Subscribing to supply fan speed sensor (PDO {} type {})", SENSOR_SUPPLY_FAN_SPEED,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_SUPPLY_FAN_SPEED, TYPE_CN_UINT8);
            logger.debug("Supply fan speed sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to supply fan speed sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust fan speed sensor (PDO 75).
     */
    public void subscribeToExhaustFanSpeedSensor() {
        try {
            logger.debug("Subscribing to exhaust fan speed sensor (PDO {} type {})", SENSOR_EXHAUST_FAN_SPEED,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_EXHAUST_FAN_SPEED, TYPE_CN_UINT8);
            logger.debug("Exhaust fan speed sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust fan speed sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to supply fan speed set sensor (PDO 76).
     */
    public void subscribeToSupplyFanSpeedSetSensor() {
        try {
            logger.debug("Subscribing to supply fan speed set sensor (PDO {} type {})", SENSOR_SUPPLY_FAN_SPEED_SET,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_SUPPLY_FAN_SPEED_SET, TYPE_CN_UINT8);
            logger.debug("Supply fan speed set sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to supply fan speed set sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust fan speed set sensor (PDO 77).
     */
    public void subscribeToExhaustFanSpeedSetSensor() {
        try {
            logger.debug("Subscribing to exhaust fan speed set sensor (PDO {} type {})", SENSOR_EXHAUST_FAN_SPEED_SET,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_EXHAUST_FAN_SPEED_SET, TYPE_CN_UINT8);
            logger.debug("Exhaust fan speed set sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust fan speed set sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to supply fan speed percentage sensor (PDO 66).
     */
    public void subscribeToSupplyFanSpeedPercentageSensor() {
        try {
            logger.debug("Subscribing to supply fan speed percentage sensor (PDO {} type {})",
                    SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE, TYPE_CN_UINT8);
            logger.debug("Supply fan speed percentage sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to supply fan speed percentage sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust fan speed percentage sensor (PDO 67).
     */
    public void subscribeToExhaustFanSpeedPercentageSensor() {
        try {
            logger.debug("Subscribing to exhaust fan speed percentage sensor (PDO {} type {})",
                    SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE, TYPE_CN_UINT8);
            logger.debug("Exhaust fan speed percentage sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust fan speed percentage sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to supply fan speed percentage set sensor (PDO 68).
     */
    public void subscribeToSupplyFanSpeedPercentageSetSensor() {
        try {
            logger.debug("Subscribing to supply fan speed percentage set sensor (PDO {} type {})",
                    SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE_SET, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE_SET, TYPE_CN_UINT8);
            logger.debug("Supply fan speed percentage set sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to supply fan speed percentage set sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust fan speed percentage set sensor (PDO 69).
     */
    public void subscribeToExhaustFanSpeedPercentageSetSensor() {
        try {
            logger.debug("Subscribing to exhaust fan speed percentage set sensor (PDO {} type {})",
                    SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE_SET, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE_SET, TYPE_CN_UINT8);
            logger.debug("Exhaust fan speed percentage set sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust fan speed percentage set sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to bypass state sensor (PDO 81).
     */
    public void subscribeToBypassStateSensor() {
        try {
            logger.debug("Subscribing to bypass state sensor (PDO {} type {})", SENSOR_BYPASS_STATE, TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_BYPASS_STATE, TYPE_CN_UINT8);
            logger.debug("Bypass state sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to bypass state sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to preheater state sensor (PDO 82).
     */
    public void subscribeToPreheaterStateSensor() {
        try {
            logger.debug("Subscribing to preheater state sensor (PDO {} type {})", SENSOR_PREHEATER_STATE,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_PREHEATER_STATE, TYPE_CN_UINT8);
            logger.debug("Preheater state sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to preheater state sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to current humidity sensor (PDO 10).
     */
    public void subscribeToCurrentHumiditySensor() {
        try {
            logger.debug("Subscribing to current humidity sensor (PDO {} type {})", SENSOR_CURRENT_HUMIDITY,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_CURRENT_HUMIDITY, TYPE_CN_UINT8);
            logger.debug("Current humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to current humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to target humidity sensor (PDO 11).
     */
    public void subscribeToTargetHumiditySensor() {
        try {
            logger.debug("Subscribing to target humidity sensor (PDO {} type {})", SENSOR_TARGET_HUMIDITY,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_TARGET_HUMIDITY, TYPE_CN_UINT8);
            logger.debug("Target humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to target humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to humidifier humidity sensor (PDO 209).
     */
    public void subscribeToHumidifierHumiditySensor() {
        try {
            logger.debug("Subscribing to humidifier humidity sensor (PDO {} type {})", SENSOR_HUMIDIFIER_HUMIDITY,
                    TYPE_CN_UINT8);
            connector.sendRpdoRequest(SENSOR_HUMIDIFIER_HUMIDITY, TYPE_CN_UINT8);
            logger.debug("Humidifier humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to humidifier humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to week profile active sensor (PDO 12).
     */
    public void subscribeToWeekProfileActiveSensor() {
        try {
            logger.debug("Subscribing to week profile active sensor (PDO {} type {})", 12, TYPE_CN_UINT8);
            connector.sendRpdoRequest(12, TYPE_CN_UINT8);
            logger.debug("Week profile active sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to week profile active sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to global allergen mode sensor (PDO 32).
     */
    public void subscribeToGlobalAllergenModeSensor() {
        try {
            logger.debug("Subscribing to global allergen mode sensor (PDO {} type {})", 32, TYPE_CN_UINT8);
            connector.sendRpdoRequest(32, TYPE_CN_UINT8);
            logger.debug("Global allergen mode sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to global allergen mode sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to EWT speed sensor (PDO 88).
     */
    public void subscribeToEwtSpeedSensor() {
        try {
            logger.debug("Subscribing to EWT speed sensor (PDO {} type {})", 88, TYPE_CN_UINT8);
            connector.sendRpdoRequest(88, TYPE_CN_UINT8);
            logger.debug("EWT speed sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to EWT speed sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to EWT position sensor (PDO 89).
     */
    public void subscribeToEwtPositionSensor() {
        try {
            logger.debug("Subscribing to EWT position sensor (PDO {} type {})", 89, TYPE_CN_UINT8);
            connector.sendRpdoRequest(89, TYPE_CN_UINT8);
            logger.debug("EWT position sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to EWT position sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to enthalpy state sensor (PDO 96).
     */
    public void subscribeToEnthalpyStateSensor() {
        try {
            logger.debug("Subscribing to enthalpy state sensor (PDO {} type {})", 96, TYPE_CN_UINT8);
            connector.sendRpdoRequest(96, TYPE_CN_UINT8);
            logger.debug("Enthalpy state sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to enthalpy state sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to frost protection speed sensor (PDO 97).
     */
    public void subscribeToFrostProtectionSpeedSensor() {
        try {
            logger.debug("Subscribing to frost protection speed sensor (PDO {} type {})", 97, TYPE_CN_UINT8);
            connector.sendRpdoRequest(97, TYPE_CN_UINT8);
            logger.debug("Frost protection speed sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to frost protection speed sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to frost protection loss sensor (PDO 98).
     */
    public void subscribeToFrostProtectionLossSensor() {
        try {
            logger.debug("Subscribing to frost protection loss sensor (PDO {} type {})", 98, TYPE_CN_UINT8);
            connector.sendRpdoRequest(98, TYPE_CN_UINT8);
            logger.debug("Frost protection loss sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to frost protection loss sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to frost protection timeout sensor (PDO 99).
     */
    public void subscribeToFrostProtectionTimeoutSensor() {
        try {
            logger.debug("Subscribing to frost protection timeout sensor (PDO {} type {})", 99, TYPE_CN_UINT8);
            connector.sendRpdoRequest(99, TYPE_CN_UINT8);
            logger.debug("Frost protection timeout sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to frost protection timeout sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to HCE present sensor (PDO 200).
     */
    public void subscribeToHcePresentSensor() {
        try {
            logger.debug("Subscribing to HCE present sensor (PDO {} type {})", 200, TYPE_CN_UINT8);
            connector.sendRpdoRequest(200, TYPE_CN_UINT8);
            logger.debug("HCE present sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to HCE present sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to outdoor temperature in sensor (PDO 2).
     */
    public void subscribeToOutdoorTemperatureInSensor() {
        try {
            logger.debug("Subscribing to outdoor temperature in sensor (PDO 2, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(2, TYPE_CN_UINT8);
            logger.debug("Outdoor temperature in sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to outdoor temperature in sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to outdoor temperature out sensor (PDO 3).
     */
    public void subscribeToOutdoorTemperatureOutSensor() {
        try {
            logger.debug("Subscribing to outdoor temperature out sensor (PDO 3, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(3, TYPE_CN_UINT8);
            logger.debug("Outdoor temperature out sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to outdoor temperature out sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to indoor temperature in sensor (PDO 4).
     */
    public void subscribeToIndoorTemperatureInSensor() {
        try {
            logger.debug("Subscribing to indoor temperature in sensor (PDO 4, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(4, TYPE_CN_UINT8);
            logger.debug("Indoor temperature in sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to indoor temperature in sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to indoor temperature out sensor (PDO 5).
     */
    public void subscribeToIndoorTemperatureOutSensor() {
        try {
            logger.debug("Subscribing to indoor temperature out sensor (PDO 5, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(5, TYPE_CN_UINT8);
            logger.debug("Indoor temperature out sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to indoor temperature out sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to EWT temperature sensor (PDO 100).
     */
    public void subscribeToEwtTemperatureSensor() {
        try {
            logger.debug("Subscribing to EWT temperature sensor (PDO 100, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(100, TYPE_CN_UINT8);
            logger.debug("EWT temperature sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to EWT temperature sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to cooker temperature sensor (PDO 101).
     */
    public void subscribeToCookerTemperatureSensor() {
        try {
            logger.debug("Subscribing to cooker temperature sensor (PDO 101, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(101, TYPE_CN_UINT8);
            logger.debug("Cooker temperature sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to cooker temperature sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to heater temperature sensor (PDO 102).
     */
    public void subscribeToHeaterTemperatureSensor() {
        try {
            logger.debug("Subscribing to heater temperature sensor (PDO 102, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(102, TYPE_CN_UINT8);
            logger.debug("Heater temperature sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to heater temperature sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to pre-heater temperature sensor (PDO 103).
     */
    public void subscribeToPreHeaterTemperatureSensor() {
        try {
            logger.debug("Subscribing to pre-heater temperature sensor (PDO 103, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(103, TYPE_CN_UINT8);
            logger.debug("Pre-heater temperature sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to pre-heater temperature sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to indoor humidity sensor (PDO 104).
     */
    public void subscribeToIndoorHumiditySensor() {
        try {
            logger.debug("Subscribing to indoor humidity sensor (PDO 104, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(104, TYPE_CN_UINT8);
            logger.debug("Indoor humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to indoor humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust humidity sensor (PDO 13).
     */
    public void subscribeToExhaustHumiditySensor() {
        try {
            logger.debug("Subscribing to exhaust humidity sensor (PDO 13, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(13, TYPE_CN_UINT8);
            logger.debug("Exhaust humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to indoor humidity 2 sensor (PDO 14).
     */
    public void subscribeToIndoorHumidity2Sensor() {
        try {
            logger.debug("Subscribing to indoor humidity 2 sensor (PDO 14, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(14, TYPE_CN_UINT8);
            logger.debug("Indoor humidity 2 sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to indoor humidity 2 sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to exhaust humidity 2 sensor (PDO 15).
     */
    public void subscribeToExhaustHumidity2Sensor() {
        try {
            logger.debug("Subscribing to exhaust humidity 2 sensor (PDO 15, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(15, TYPE_CN_UINT8);
            logger.debug("Exhaust humidity 2 sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to exhaust humidity 2 sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to indoor humidity 3 sensor (PDO 16).
     */
    public void subscribeToIndoorHumidity3Sensor() {
        try {
            logger.debug("Subscribing to indoor humidity 3 sensor (PDO 16, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(16, TYPE_CN_UINT8);
            logger.debug("Indoor humidity 3 sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to indoor humidity 3 sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to ComfoSupply humidity sensor (PDO 105).
     */
    public void subscribeToComfoSupplyHumiditySensor() {
        try {
            logger.debug("Subscribing to ComfoSupply humidity sensor (PDO 105, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(105, TYPE_CN_UINT8);
            logger.debug("ComfoSupply humidity sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to ComfoSupply humidity sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to T1 sensor present sensor (PDO 17).
     */
    public void subscribeToT1SensorPresentSensor() {
        try {
            logger.debug("Subscribing to T1 sensor present sensor (PDO 17, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(17, TYPE_CN_UINT8);
            logger.debug("T1 sensor present sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to T1 sensor present sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to T2 sensor present sensor (PDO 18).
     */
    public void subscribeToT2SensorPresentSensor() {
        try {
            logger.debug("Subscribing to T2 sensor present sensor (PDO 18, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(18, TYPE_CN_UINT8);
            logger.debug("T2 sensor present sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to T2 sensor present sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to T3 sensor present sensor (PDO 21).
     */
    public void subscribeToT3SensorPresentSensor() {
        try {
            logger.debug("Subscribing to T3 sensor present sensor (PDO 21, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(21, TYPE_CN_UINT8);
            logger.debug("T3 sensor present sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to T3 sensor present sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to temperature unit sensor (PDO 208).
     */
    public void subscribeToTemperatureUnitSensor() {
        try {
            logger.debug("Subscribing to temperature unit sensor (PDO 208, type {})", TYPE_CN_UINT8);
            connector.sendRpdoRequest(208, TYPE_CN_UINT8);
            logger.debug("Temperature unit sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to temperature unit sensor: {}", e.getMessage());
        }
    }

    /**
     * Subscribe to airflow constraints sensor (PDO 230).
     */
    public void subscribeToAirflowConstraintsSensor() {
        try {
            logger.debug("Subscribing to airflow constraints sensor (PDO 230, type {})", TYPE_CN_UINT64);
            connector.sendRpdoRequest(230, TYPE_CN_UINT64);
            logger.debug("Airflow constraints sensor subscription request sent successfully");
        } catch (IOException e) {
            logger.warn("Failed to subscribe to airflow constraints sensor: {}", e.getMessage());
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
            logger.debug("RPDO notification for sensor ID: {}", sensorId);

            // Route to appropriate handler based on sensor ID
            // Extract sensor data based on sensor type
            int sensorValue = extractSensorValue(sensorId, payload);

            SensorDataCallback callback = sensorCallback;
            if (callback != null) {
                logger.debug("Invoking callback with sensorId={}, value={}", sensorId, sensorValue);
                callback.onSensorDataReceived(sensorId, sensorValue);
            } else {
                logger.warn("Callback is null, cannot deliver sensor data for sensor {}", sensorId);
            }
        } catch (Exception e) {
            logger.error("Error handling RPDO notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Extract sensor value from payload based on sensor ID.
     * Handles different data types (UINT8, UINT16, UINT32).
     *
     * @param sensorId the sensor ID
     * @param payload the RPDO notification payload
     * @return the extracted sensor value
     */
    private int extractSensorValue(final int sensorId, final byte[] payload) {
        // For now, support all Phase 1 sensors which are all UINT8 type
        // Bytes 0-1 are sensor ID, byte 2 is padding/field tag for value, byte 3 is the actual value
        if (payload.length >= 4) {
            return payload[3] & 0xFF;
        }
        return 0;
    }

    /**
     * Apply value corrections based on sensor type.
     * Handles temperature division, boolean mapping, unit conversion, etc.
     *
     * @param sensorId the sensor ID
     * @param rawValue the raw sensor value
     * @return the corrected sensor value
     */
    private double correctSensorValue(final int sensorId, final int rawValue) {
        // Temperature sensors: divide by 10 to get actual temperature
        switch (sensorId) {
            case 2: // SENSOR_OUTDOOR_TEMPERATURE_IN
            case 3: // SENSOR_OUTDOOR_TEMPERATURE_OUT
            case 4: // SENSOR_INDOOR_TEMPERATURE_IN
            case 5: // SENSOR_INDOOR_TEMPERATURE_OUT
            case 100: // SENSOR_EWT_TEMPERATURE
            case 101: // SENSOR_COOKER_TEMPERATURE
            case 102: // SENSOR_HEATER_TEMPERATURE
            case 103: // SENSOR_PRE_HEATER_TEMPERATURE
            case 104: // SENSOR_INDOOR_HUMIDITY (actually part of temperature sensors in raw form)
                return rawValue / 10.0;
            default:
                // No correction needed for other sensors
                return rawValue;
        }
    }
}

/**
 * Callback interface for receiving sensor data updates from the gateway.
 */
@NonNullByDefault
interface SensorDataCallback {
    /**
     * Called when sensor data is received from the gateway.
     *
     * @param sensorId the sensor ID
     * @param value the sensor value
     */
    void onSensorDataReceived(int sensorId, int value);
}
