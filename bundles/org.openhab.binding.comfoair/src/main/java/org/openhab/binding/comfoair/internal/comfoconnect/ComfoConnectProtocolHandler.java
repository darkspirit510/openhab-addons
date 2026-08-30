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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.component.Gateway;
import org.openhab.binding.comfoair.internal.comfoconnect.component.KeepAliveWorker;
import org.openhab.binding.comfoair.internal.comfoconnect.component.SensorHandler;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.HexConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ParsedFrame;
import org.openhab.binding.comfoair.internal.comfoconnect.response.Payload;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.MessageLite;
import com.zehnder.proto.Zehnder;
import com.zehnder.proto.Zehnder.GatewayOperation;
import com.zehnder.proto.Zehnder.GatewayOperation.GatewayResult;
import com.zehnder.proto.Zehnder.GatewayOperation.OperationType;

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
 * - Message dispatching and routing
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectProtocolHandler {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectProtocolHandler.class);

    private static final long REQUEST_TIMEOUT_SEC = 5;
    private static final int REQUEST_RETRY_COUNT = 3;
    private static final long REQUEST_RETRY_DELAY_SEC = 5;
    private static final int MAX_REFERENCE = 0xFFFFFF; // 24-bit reference

    private final ComfoConnectConnector connector;
    private final ScheduledExecutorService scheduler;
    private final HexConverter hexConverter = new HexConverter();
    private final SensorHandler sensorManager;
    private final RequestExecutor requestExecutor;

    private final int pinCode;
    private final boolean autoTakeover;
    private volatile boolean sessionActive = false;

    private volatile int nextReference = 1;
    private final Map<Integer, PendingRequest<?>> pendingRequests = new HashMap<>();

    private @Nullable SensorHandler sensorHandler;
    private @Nullable Runnable connectionErrorCallback;
    private @Nullable KeepAliveWorker keepAliveWorker;
    private @Nullable Integer ventilationNodeId;

    /**
     * Get the sensor manager for external access.
     *
     * @return the sensor manager
     */
    public SensorHandler getSensorManager() {
        return sensorManager;
    }

    /**
     * Container for pending request information.
     */
    private static class PendingRequest<T> {
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
        this.scheduler = scheduler;
        this.pinCode = pinCode;
        this.autoTakeover = autoTakeover;
        this.requestExecutor = request -> sendRequestWithRetry(request, byte[].class, REQUEST_TIMEOUT_SEC);
        this.sensorManager = new SensorHandler(connector, this::handleConnectionError, null);
    }

    /**
     * Set the sensor data handler for receiving sensor updates.
     *
     * @param handler the handler to invoke when sensor data arrives, or null to unregister
     */
    public void setSensorHandler(final @Nullable SensorHandler handler) {
        this.sensorHandler = handler;
    }

    /**
     * Set the callback to be invoked when a connection error occurs.
     *
     * @param callback the callback to invoke on connection error
     */
    public void setConnectionErrorCallback(final Runnable callback) {
        this.connectionErrorCallback = callback;
    }

    /**
     * Set the callback to be invoked when keep-alive fails.
     *
     * @param callback the callback to invoke on keep-alive failure
     */
    public void setKeepAliveFailureCallback(final Runnable callback) {
        this.keepAliveWorker = new KeepAliveWorker(connector, scheduler, this, callback);
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

        // Session initialization
        new Gateway(requestExecutor).ensureRegistration(connector.clientUuid(), pinCode);
        startSession();
        sessionActive = true;

        startKeepAliveTimer();

        // Discover the ventilation node ID for RMI requests
        discoverVentilationNode();

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
    public <T> T sendRequestSync(final MessageLite request, final Class<T> responseClass, final long timeoutSec)
            throws IOException, TimeoutException, InterruptedException {
        CompletableFuture<T> future = sendRequestAsync(request, responseClass);

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }

            String message = cause != null ? cause.getMessage() : "Unknown error";
            throw new IOException("Request failed: " + message, cause);
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
    private <T> T sendRequestWithRetry(final MessageLite request, final Class<T> responseClass, final long timeoutSec)
            throws IOException, InterruptedException {
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
    public <T> CompletableFuture<T> sendRequestAsync(final MessageLite request, final Class<T> responseClass)
            throws IOException {
        int reference = allocateReference();
        CompletableFuture<T> future = new CompletableFuture<>();
        PendingRequest<T> pendingRequest = new PendingRequest<>(reference, future, responseClass);

        synchronized (pendingRequests) {
            pendingRequests.put(reference, pendingRequest);
        }

        try {
            OperationType opType = getOperationType(request);

            logger.info("Sending {} (reference {})", request.getClass().getSimpleName(), reference);
            byte[] frame = connector.getFramer().createReferencedFrame(opType, reference, request);
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
     * Allocate a unique reference number for a request.
     *
     * @return a unique reference
     */
    public synchronized int allocateReference() {
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
     * @param payload the response payload
     * @param result the gateway result from the operation
     */
    public void completeRequest(final int reference, final Payload payload, final GatewayResult result) {
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
     * Handle a request timeout.
     *
     * @param reference the request reference that timed out
     */
    public void handleRequestTimeout(final int reference) {
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
     * Parse response based on expected type.
     *
     * @param responseClass the expected response class
     * @param payload the payload
     * @return parsed response object
     */
    private Object parseResponse(final Class<?> responseClass, final Payload payload) throws IOException {
        if (responseClass == byte[].class) {
            return payload.content;
        }

        throw new IOException("Unknown response type: " + responseClass.getName());
    }

    /**
     * Get the operation type for a given request.
     *
     * @param request the request message
     * @return the corresponding operation type
     */
    private OperationType getOperationType(final MessageLite request) {
        String requestType = request.getClass().getSimpleName();

        if (requestType.contains("RegisterApp")) {
            return OperationType.RegisterAppRequestType;
        } else if (requestType.contains("StartSession")) {
            return OperationType.StartSessionRequestType;
        } else if (requestType.contains("ListRegisteredApps")) {
            return OperationType.ListRegisteredAppsRequestType;
        } else if (requestType.contains("KeepAlive")) {
            return OperationType.KeepAliveType;
        } else if (requestType.contains("CloseSession")) {
            return OperationType.CloseSessionRequestType;
        } else if (requestType.contains("CnNodeRequest")) {
            return OperationType.CnNodeRequestType;
        }
        throw new IllegalArgumentException("Unknown request type: " + requestType);
    }

    /**
     * Start a session with the gateway, with optional takeover of existing sessions.
     *
     * @throws IOException if start fails or session conflict exists
     * @throws InterruptedException if interrupted
     */
    private void startSession() throws IOException, InterruptedException {
        logger.debug("Starting session with gateway (autoTakeover={})", autoTakeover);

        Zehnder.StartSessionRequest request = Zehnder.StartSessionRequest.newBuilder().setTakeover(autoTakeover)
                .build();

        requestExecutor.execute(request);
        logger.info("Session started successfully");
    }

    /**
     * Close the session with the gateway.
     *
     * @throws IOException if close fails
     */
    private void closeSession() throws IOException {
        logger.debug("Closing session with gateway");

        Zehnder.CloseSessionRequest closeSessionRequest = Zehnder.CloseSessionRequest.newBuilder().build();

        byte[] frame = connector.getFramer()
                .createReferencedFrame(GatewayOperation.OperationType.CloseSessionRequestType, closeSessionRequest);

        try {
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
        if (this.keepAliveWorker != null) {
            this.keepAliveWorker.startKeepAliveTimer();
        }
    }

    /**
     * Stop the keep-alive timer.
     */
    public void stopKeepAliveTimer() {
        if (this.keepAliveWorker != null) {
            this.keepAliveWorker.stopKeepAliveTimer();
        }
    }

    public boolean isSessionActive() {
        return sessionActive;
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
            ParsedFrame parsed = connector.getFramer().parseFrame(frame);

            if (parsed == null) {
                logger.warn("Failed to parse frame");
                return;
            }

            GatewayOperation operation = GatewayOperation.parseFrom(parsed.command());
            logger.info("Received operation: type={}, reference={}, result={}", operation.getType(),
                    operation.getReference(), operation.getResult());

            if (operation.getResult() != GatewayOperation.GatewayResult.OK) {
                logger.warn("Gateway returned error: {} - {}", operation.getResult(), operation.getResultDescription());
            }

            Payload payload = new Payload(parsed.payload());

            switch (operation.getType()) {
                case KeepAliveType:
                    break;

                case RegisterAppConfirmType:
                case StartSessionConfirmType:
                case CloseSessionConfirmType:
                case ListRegisteredAppsConfirmType:
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), payload, operation.getResult());
                    }
                    break;

                case CnNodeNotificationType:
                    logger.info("Handling node notification: type={}, payload length={}", operation.getType(),
                            parsed.payload().length);
                    handleNodeNotification(payload);
                    break;

                case CnRpdoNotificationType:
                case CnAlarmNotificationType:
                    logger.info("Handling notification: type={}, payload length={}", operation.getType(),
                            parsed.payload().length);
                    handleNotification(operation, payload);
                    break;

                case CnRmiResponseType:
                case CnRmiAsyncResponseType:
                    logger.info("Handling RMI response: type={}, payload length={}", operation.getType(),
                            parsed.payload().length);
                    handleRmiResponse(operation, payload);
                    break;

                default:
                    // Other async responses
                    if (operation.getReference() > 0) {
                        completeRequest(operation.getReference(), payload, operation.getResult());
                    }

                    break;
            }

        } catch (Exception e) {
            logger.error("Error handling incoming message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle asynchronous notifications from the gateway.
     *
     * @param operation the gateway operation
     * @param payload the payload
     */
    private void handleNotification(final GatewayOperation operation, final Payload payload) {
        logger.info("Received notification: type={}, payload length={}", operation.getType(), payload.length);

        if (operation.getType() == GatewayOperation.OperationType.CnRpdoNotificationType) {
            logger.info("Processing CnRpdoNotification with {} bytes", payload.length);
            handleRpdoNotification(payload);
        } else {
            logger.info("Ignoring notification type: {}", operation.getType());
        }
    }

    /**
     * Handle incoming node notification to discover the ventilation unit.
     *
     * @param payload the node notification payload
     */
    private void handleNodeNotification(final Payload payload) {
        try {
            Zehnder.CnNodeNotification nodeNotification = Zehnder.CnNodeNotification.parseFrom(payload.content);
            int nodeId = nodeNotification.getNodeId();
            int productId = nodeNotification.getProductId();

            logger.debug("Node notification: nodeId={}, productId={}", nodeId, productId);

            // Set the ventilation node ID
            setVentilationNodeId(nodeId);
        } catch (Exception e) {
            logger.error("Error handling node notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle incoming RPDO notification from the gateway.
     *
     * @param payload the RPDO notification payload
     */
    private void handleRpdoNotification(final Payload payload) {
        try {
            logger.debug("handleRpdoNotification: payload length={}, hex={}", payload.length,
                    hexConverter.toHex(payload.content));

            // Parse the payload as a protobuf CnRpdoNotification message
            Zehnder.CnRpdoNotification notification = Zehnder.CnRpdoNotification.parseFrom(payload.content);

            int sensorId = notification.getPdid();
            byte[] dataBytes = notification.getData().toByteArray();
            logger.debug("Parsed RPDO: pdid={}, data length={}, data hex={}", sensorId, dataBytes.length,
                    hexConverter.toHex(dataBytes));

            Sensor sensor = Sensors.findById(sensorId).orElse(null);

            if (sensor == null) {
                logger.warn("Received notification for unknown sensor with ID {}, ignoring it", sensorId);
                return;
            }

            logger.info("RPDO notification for sensor: {}", sensor);

            // Route to appropriate handler based on sensor ID

            if (sensorHandler != null) {
                sensorHandler.onSensorDataReceived(sensor, notification);
            }
        } catch (Exception e) {
            logger.error("Error handling RPDO notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle incoming RMI response from the gateway.
     *
     * @param operation the gateway operation
     * @param payload the payload containing the RMI response
     */
    private void handleRmiResponse(final GatewayOperation operation, final Payload payload) {
        try {
            Zehnder.CnRmiResponse response = Zehnder.CnRmiResponse.parseFrom(payload.content);
            byte[] data = response.getMessage().toByteArray();

            logger.debug("RMI response: result={}, data length={}", response.getResult(), data.length);

            if (data.length == 0) {
                logger.warn("Empty RMI response data");
                return;
            }

            // For bypass state, the last byte of the response contains the state
            // (0x00 = AUTO, 0x01 = ON, 0x02 = OFF)
            int state = data[data.length - 1] & 0xFF;
            logger.debug("RMI response state value: {}", state);

            // Route to the sensor callback for bypass state

            if (sensorHandler != null) {
                // Use the BYPASS_STATE sensor (if defined)
                Sensors.findByChannelId("bypassState").ifPresent(sensor -> {
                    // Create a pseudo-RPDO notification for the sensor callback
                    Zehnder.CnRpdoNotification message = Zehnder.CnRpdoNotification.newBuilder().setPdid(sensor.id)
                            .setData(com.google.protobuf.ByteString.copyFrom(new byte[] { (byte) state })).build();
                    sensorHandler.onSensorDataReceived(sensor, message);
                });
            }
        } catch (Exception e) {
            logger.error("Error handling RMI response: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribe to a sensor.
     * This is the generic method that replaces all individual subscribeToXxxSensor methods.
     *
     * @param sensor the sensor to subscribe to
     * @param sensorType the sensor data type (from SensorValueType)
     */
    public void subscribeToSensor(final Sensor sensor, final SensorValueType sensorType) {
        sensorManager.subscribeToSensor(sensor, sensorType);
    }

    /**
     * Unsubscribe from a sensor.
     * This sends a CnRpdoRequest without the type field, which according to the protocol
     * will delete a previously registered RPDO with the given PDID.
     *
     * @param sensor the sensor to unsubscribe from
     */
    public void unsubscribeFromSensor(final Sensor sensor) {
        sensorManager.unsubscribeFromSensor(sensor);
    }

    /**
     * Send an RMI request to the gateway using the discovered ventilation node ID.
     *
     * @param unit the RMI unit ID (e.g., UNIT_SCHEDULE = 0x08)
     * @param subunit the RMI subunit ID (e.g., SUBUNIT_02 = 0x02)
     * @param propertyId the RMI property ID (e.g., 0x01 for bypass state)
     * @throws IOException if send fails
     */
    public void sendRmiRequest(final int unit, final int subunit, final int propertyId) throws IOException {
        sendRmiRequest(getVentilationNodeId(), unit, subunit, propertyId);
    }

    /**
     * Send an RMI request to the gateway with a specific node ID.
     *
     * @param nodeId the ComfoNet node ID (typically 1 for ventilation unit)
     * @param unit the RMI unit ID (e.g., UNIT_SCHEDULE = 0x08)
     * @param subunit the RMI subunit ID (e.g., SUBUNIT_02 = 0x02)
     * @param propertyId the RMI property ID (e.g., 0x01 for bypass state)
     * @throws IOException if send fails
     */
    public void sendRmiRequest(final int nodeId, final int unit, final int subunit, final int propertyId)
            throws IOException {
        // Construct RMI message payload: 0x83 (read request), unit, subunit, propertyId
        byte[] rmiMessage = new byte[] { (byte) 0x83, (byte) unit, (byte) subunit, (byte) propertyId };

        try {
            connector.sendRmiRequest(nodeId, rmiMessage);
        } catch (IOException e) {
            handleConnectionError(e);
            throw e;
        }
    }

    /**
     * Check if an error message indicates a connection error that should trigger reconnection.
     *
     * @param errorMsg the error message to check
     * @return true if the error is connection-related, false otherwise
     */
    public boolean isConnectionError(final @Nullable String errorMsg) {
        return errorMsg != null && (errorMsg.contains("Not connected to gateway") || errorMsg.contains("connection")
                || errorMsg.contains("socket"));
    }

    /**
     * Get the connection error callback.
     *
     * @return the connection error callback, or null if not set
     */
    @Nullable
    Runnable getConnectionErrorCallback() {
        return connectionErrorCallback;
    }

    /**
     * Handle connection errors and trigger automatic reconnection if appropriate.
     *
     * @param e the IOException that occurred
     */
    private void handleConnectionError(final IOException e) {
        String errorMsg = e.getMessage();
        if (errorMsg != null && isConnectionError(errorMsg)) {
            logger.warn("Connection error detected: {}. Scheduling automatic reconnection.", errorMsg);

            // Notify the handler to trigger reconnection
            Runnable reconnectCallback = connectionErrorCallback;
            if (reconnectCallback != null) {
                reconnectCallback.run();
            }
        }
    }

    /**
     * Discover the ventilation node ID by sending a CnNodeRequest.
     * This will trigger CnNodeNotification messages from the gateway.
     */
    private void discoverVentilationNode() {
        try {
            logger.debug("Discovering ventilation node ID");
            sendRequestSync(Zehnder.CnNodeRequest.newBuilder().build(), byte[].class, REQUEST_TIMEOUT_SEC);
            // The node ID will be set via handleNodeNotification when the response arrives
        } catch (Exception e) {
            logger.warn("Failed to discover ventilation node: {}", e.getMessage());
            // Fallback to node ID 1 if discovery fails
            ventilationNodeId = 1;
        }
    }

    /**
     * Get the ventilation node ID, discovering it if not already known.
     *
     * @return the ventilation node ID, or 1 if not discovered
     */
    public int getVentilationNodeId() {
        Integer nodeId = ventilationNodeId;
        return nodeId != null ? nodeId : 1;
    }

    /**
     * Set the ventilation node ID (called when a CnNodeNotification is received).
     *
     * @param nodeId the node ID
     */
    public void setVentilationNodeId(int nodeId) {
        this.ventilationNodeId = nodeId;
        logger.info("Ventilation node ID discovered: {}", nodeId);
    }
}
