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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Bridge handler for ComfoConnect LAN gateway devices (newer Q-series).
 *
 * Manages:
 * - TCP connection to the gateway
 * - Protocol authentication and session management
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
    private @Nullable Future<?> messageConsumerTask;

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

        updateStatus(ThingStatus.UNKNOWN);

        String hostname = Objects.requireNonNull(config.hostname);

        // Use configured clientUuid or default from constant
        UUID clientUuid;
        if (config.clientUuid != null && !config.clientUuid.isEmpty()) {
            try {
                clientUuid = UUID.fromString(Objects.requireNonNull(config.clientUuid));
                logger.debug("Using configured client UUID: {}", clientUuid);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid client UUID format, using default: {}", config.clientUuid);
                clientUuid = UUID.fromString(ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID);
            }
        } else {
            clientUuid = UUID.fromString(ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID);
            logger.debug("Using default client UUID: {}", clientUuid);
        }

        // Use gateway UUID from discovery or derive it as fallback
        UUID gatewayUuid;
        if (config.gatewayUuid != null && !config.gatewayUuid.isEmpty()) {
            try {
                gatewayUuid = UUID.fromString(Objects.requireNonNull(config.gatewayUuid));
                logger.debug("Using configured gateway UUID: {}", gatewayUuid);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid gateway UUID format, attempting discovery: {}", config.gatewayUuid);
                gatewayUuid = discoverGatewayUuid(hostname, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
                if (gatewayUuid == null) {
                    // Discovery failed, use fallback
                    logger.warn("Gateway discovery failed, using derived UUID as fallback");
                    gatewayUuid = UUID.nameUUIDFromBytes(
                            ("comfoair-gateway-" + hostname + ":" + ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT)
                                    .getBytes());
                }
            }
        } else {
            logger.debug("No gateway UUID configured, attempting discovery");
            gatewayUuid = discoverGatewayUuid(hostname, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
            if (gatewayUuid == null) {
                // Discovery failed, use fallback
                logger.debug("Gateway discovery failed, using derived UUID as fallback");
                gatewayUuid = UUID.nameUUIDFromBytes(
                        ("comfoair-gateway-" + hostname + ":" + ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT)
                                .getBytes());
            }
        }

        ComfoConnectTcpConnector connector = new ComfoConnectTcpConnector(hostname,
                ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT, clientUuid, gatewayUuid);
        this.connector = connector;

        int pinCode;
        if (config.pin == null || config.pin.isEmpty()) {
            pinCode = 0; // Default PIN when not configured
            logger.debug("Using default PIN (0) as none was configured");
        } else {
            try {
                pinCode = Integer.parseInt(Objects.requireNonNull(config.pin));
            } catch (NumberFormatException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Gateway PIN must be a numeric value");
                return;
            }
        }

        ComfoConnectProtocolHandler protocolHandler = new ComfoConnectProtocolHandler(connector, pinCode,
                config.autoTakeover, scheduler);
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

            // Register sensor data callback before protocol initialization
            protocolHandler.setSensorCallback(this::handleSensorData);

            // Start the message consumer loop BEFORE protocol initialization
            // so responses can be received and processed
            startMessageConsumer(connector, protocolHandler);

            protocolHandler.initialize();
            logger.info("ComfoConnect bridge connected and authenticated");

            // Subscribe to sensors based on linked channels
            subscribeToLinkedChannels();

            updateStatus(ThingStatus.ONLINE);

            ScheduledFuture<?> task = connectionRetryTask;

            if (task != null) {
                task.cancel(true);
                connectionRetryTask = null;
            }
        } catch (InterruptedException e) {
            logger.warn("Connection attempt interrupted: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection interrupted");
            scheduleReconnectAttempt();
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Connection timeout: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection timeout");
            scheduleReconnectAttempt();
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            ThingStatusDetail detail = ThingStatusDetail.COMMUNICATION_ERROR;

            // Provide more specific error messages
            if (errorMsg != null && errorMsg.contains("Invalid PIN")) {
                detail = ThingStatusDetail.CONFIGURATION_ERROR;
                errorMsg = "Invalid PIN code (NOT_ALLOWED)";
            } else if (errorMsg != null && errorMsg.contains("Another app is already logged in")) {
                detail = ThingStatusDetail.CONFIGURATION_ERROR;
            }

            logger.warn("Failed to connect or authenticate with gateway: {}", errorMsg);
            updateStatus(ThingStatus.OFFLINE, detail, errorMsg);
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

    /**
     * Start the message consumer loop that processes incoming messages from the gateway.
     * This task runs in the background and continuously polls for messages from the connector's queue,
     * then dispatches them to the protocol handler for processing.
     *
     * @param connector the TCP connector with queued messages
     * @param protocolHandler the protocol handler to process messages
     */
    private void startMessageConsumer(final ComfoConnectTcpConnector connector,
            final ComfoConnectProtocolHandler protocolHandler) {
        logger.debug("Starting message consumer loop");

        messageConsumerTask = scheduler.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] message = connector.getNextMessage();
                    if (message != null) {
                        logger.trace("Message consumer: processing {} bytes", message.length);
                        protocolHandler.handleIncomingMessage(message);
                    }
                }
            } catch (Exception e) {
                logger.warn("Unexpected error in message consumer loop: {}", e.getMessage(), e);
            }
            logger.debug("Message consumer loop stopped");
        });
    }

    @Override
    public void dispose() {
        logger.info("Disposing ComfoConnect bridge: {}", getThing().getUID());

        // Unregister sensor callback
        ComfoConnectProtocolHandler protocolHandler = this.protocolHandler;
        if (protocolHandler != null) {
            protocolHandler.setSensorCallback(null);
        }

        ScheduledFuture<?> task = connectionRetryTask;
        if (task != null) {
            task.cancel(true);
            connectionRetryTask = null;
        }

        Future<?> consumerTask = messageConsumerTask;
        if (consumerTask != null) {
            consumerTask.cancel(true);
            messageConsumerTask = null;
        }

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
     * Handle sensor data received from the gateway.
     *
     * @param sensorId the sensor ID
     * @param value the sensor value
     */
    private void handleSensorData(final int sensorId, final int value) {
        logger.debug("handleSensorData called: sensorId={}, value={}", sensorId, value);

        // Route sensor data to appropriate handler based on sensor ID
        // Phase 1: Fan-related sensors
        switch (sensorId) {
            case 1: // SENSOR_OPERATING_MODE
                updateChannelState("operatingMode", new QuantityType<>(value, Units.ONE));
                break;
            case 65: // SENSOR_FAN_SPEED_MODE (Fan speed - already in use)
                handleFanSpeedUpdate(value);
                break;
            case 66: // SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE
                updateChannelState("supplyFanSpeedPercentage", new QuantityType<>(value, Units.PERCENT));
                break;
            case 67: // SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE
                updateChannelState("exhaustFanSpeedPercentage", new QuantityType<>(value, Units.PERCENT));
                break;
            case 68: // SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE_SET
                updateChannelState("supplyFanSpeedPercentageSet", new QuantityType<>(value, Units.PERCENT));
                break;
            case 69: // SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE_SET
                updateChannelState("exhaustFanSpeedPercentageSet", new QuantityType<>(value, Units.PERCENT));
                break;
            case 74: // SENSOR_SUPPLY_FAN_SPEED
                updateChannelState("supplyFanSpeed", new QuantityType<>(value, Units.ONE));
                break;
            case 75: // SENSOR_EXHAUST_FAN_SPEED
                updateChannelState("exhaustFanSpeed", new QuantityType<>(value, Units.ONE));
                break;
            case 76: // SENSOR_SUPPLY_FAN_SPEED_SET
                updateChannelState("supplyFanSpeedSet", new QuantityType<>(value, Units.ONE));
                break;
            case 77: // SENSOR_EXHAUST_FAN_SPEED_SET
                updateChannelState("exhaustFanSpeedSet", new QuantityType<>(value, Units.ONE));
                break;
            case 81: // SENSOR_BYPASS_STATE
                updateChannelState("bypassState", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 82: // SENSOR_PREHEATER_STATE
                updateChannelState("preheaterState", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 10: // SENSOR_CURRENT_HUMIDITY
                updateChannelState("currentHumidity", new QuantityType<>(value, Units.PERCENT));
                break;
            case 11: // SENSOR_TARGET_HUMIDITY
                updateChannelState("targetHumidity", new QuantityType<>(value, Units.PERCENT));
                break;
            case 209: // SENSOR_HUMIDIFIER_HUMIDITY
                updateChannelState("humidifierHumidity", new QuantityType<>(value, Units.PERCENT));
                break;
            // Phase 2: Other basic sensors
            case 12: // SENSOR_WEEK_PROFILE_ACTIVE
                updateChannelState("weekProfileActive", new QuantityType<>(value, Units.ONE));
                break;
            case 32: // SENSOR_GLOBAL_ALLERGEN_MODE
                updateChannelState("globalAllergenMode", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 88: // SENSOR_EWT_SPEED
                updateChannelState("ewtSpeed", new QuantityType<>(value, Units.HERTZ));
                break;
            case 89: // SENSOR_EWT_POSITION
                updateChannelState("ewtPosition", new QuantityType<>(value, Units.PERCENT));
                break;
            case 96: // SENSOR_ENTHALPY_STATE
                updateChannelState("enthalpyState", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 97: // SENSOR_FROST_PROTECTION_SPEED
                updateChannelState("frostProtectionSpeed", new QuantityType<>(value, Units.HERTZ));
                break;
            case 98: // SENSOR_FROST_PROTECTION_LOSS
                updateChannelState("frostProtectionLoss", new QuantityType<>(value, Units.ONE));
                break;
            case 99: // SENSOR_FROST_PROTECTION_TIMEOUT
                updateChannelState("frostProtectionTimeout", new QuantityType<>(value, Units.ONE));
                break;
            case 200: // SENSOR_HCE_PRESENT
                updateChannelState("hcePresent", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            // Phase 3: Sensors with value corrections
            // Temperature sensors (divide by 10)
            case 2: // SENSOR_OUTDOOR_TEMPERATURE_IN
                updateChannelState("outdoorTemperatureIn", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 3: // SENSOR_OUTDOOR_TEMPERATURE_OUT
                updateChannelState("outdoorTemperatureOut", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 4: // SENSOR_INDOOR_TEMPERATURE_IN
                updateChannelState("indoorTemperatureIn", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 5: // SENSOR_INDOOR_TEMPERATURE_OUT
                updateChannelState("indoorTemperatureOut", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 100: // SENSOR_EWT_TEMPERATURE
                updateChannelState("ewtTemperature", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 101: // SENSOR_COOKER_TEMPERATURE
                updateChannelState("cookerTemperature", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 102: // SENSOR_HEATER_TEMPERATURE
                updateChannelState("heaterTemperature", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 103: // SENSOR_PRE_HEATER_TEMPERATURE
                updateChannelState("preHeaterTemperature", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            case 104: // SENSOR_INDOOR_HUMIDITY (temperature from raw data)
                updateChannelState("indoorHumidity", new QuantityType<>(value / 10.0, SIUnits.CELSIUS));
                break;
            // Humidity sensors (no correction)
            case 13: // SENSOR_EXHAUST_HUMIDITY
                updateChannelState("exhaustHumidity", new QuantityType<>(value, Units.PERCENT));
                break;
            case 14: // SENSOR_INDOOR_HUMIDITY_2
                updateChannelState("indoorHumidity2", new QuantityType<>(value, Units.PERCENT));
                break;
            case 15: // SENSOR_EXHAUST_HUMIDITY_2
                updateChannelState("exhaustHumidity2", new QuantityType<>(value, Units.PERCENT));
                break;
            case 16: // SENSOR_INDOOR_HUMIDITY_3
                updateChannelState("indoorHumidity3", new QuantityType<>(value, Units.PERCENT));
                break;
            case 105: // SENSOR_COMFOSUPPLY_HUMIDITY
                updateChannelState("comfoSupplyHumidity", new QuantityType<>(value, Units.PERCENT));
                break;
            // Boolean sensors
            case 17: // SENSOR_T1_SENSOR_PRESENT
                updateChannelState("t1SensorPresent", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 18: // SENSOR_T2_SENSOR_PRESENT
                updateChannelState("t2SensorPresent", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            case 21: // SENSOR_T3_SENSOR_PRESENT
                updateChannelState("t3SensorPresent", value != 0 ? OnOffType.ON : OnOffType.OFF);
                break;
            // Mapping sensor (Temperature unit: 0=Celsius, else=Fahrenheit)
            case 208: // SENSOR_TEMPERATURE_UNIT
                updateChannelState("temperatureUnit", new StringType(value == 0 ? "Celsius" : "Fahrenheit"));
                break;
            // Phase 4: Complex sensors
            case 230: // SENSOR_AIRFLOW_CONSTRAINTS
                updateChannelState("airflowConstraints", new StringType(calculateAirflowConstraints(value)));
                break;
            default:
                logger.debug("Ignoring data for unknown sensor: {}", sensorId);
                break;
        }
    }

    /**
     * Calculate airflow constraints from the raw sensor value using bit-shifting.
     * Maps bit positions to constraint names.
     *
     * @param rawValue the raw sensor value (64-bit integer as int)
     * @return comma-separated string of active constraints, or empty string if none
     */
    private String calculateAirflowConstraints(final int rawValue) {
        // Constraint bit position mappings (from Python implementation)
        final String[] constraints = new String[64];
        constraints[2] = "Resistance";
        constraints[4] = "PreheaterNegative";
        constraints[6] = "PreheaterOutdoorTemperature";
        constraints[7] = "PreheaterLimitTa";
        constraints[8] = "PreheaterActivated";
        constraints[9] = "PreheaterErrorNTC";
        constraints[10] = "BypassErrorWet";
        constraints[11] = "BypassErrorFrost";
        constraints[12] = "BypassActivated";
        constraints[13] = "FrostProtectionMinSpeed";
        constraints[14] = "FrostProtectionOutdoor";
        constraints[15] = "FrostProtectionIndoor";
        constraints[16] = "FrostProtectionFailed";
        constraints[19] = "EnthalpyBypassLowIndoor";
        constraints[20] = "EnthalpyBypassWarmOutdoor";
        constraints[21] = "EnthalpyBypassColdOutdoor";
        constraints[22] = "EnthalpyActivated";
        constraints[23] = "CookingZoneActive";
        constraints[24] = "AnalogInput1";
        constraints[25] = "AnalogInput2";
        constraints[26] = "AnalogInput3";
        constraints[27] = "AnalogInput4";

        // Check bit 45 to see if constraints are valid
        // For now, since value is an int, we can't directly check bit 45 (which would be in a 64-bit value)
        // So we'll process the constraint bits directly

        StringBuilder constraintList = new StringBuilder();
        for (int bit = 0; bit < 32; bit++) { // Check 32 bits since we're using int
            if ((rawValue & (1 << bit)) != 0 && bit < constraints.length && constraints[bit] != null) {
                if (constraintList.length() > 0) {
                    constraintList.append(", ");
                }
                constraintList.append(constraints[bit]);
            }
        }

        return constraintList.toString();
    }

    /**
     * Update the state of a channel on this bridge.
     *
     * @param channelId the channel ID to update
     * @param state the new state for the channel
     */
    private void updateChannelState(final String channelId, final org.openhab.core.types.State state) {
        try {
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), channelId);
            logger.debug("Updating channel {} to state {}", channelId, state);
            updateState(channelUID, state);
        } catch (Exception e) {
            logger.error("Error updating channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Handle an update to the fan speed value.
     *
     * @param value the fan speed value (0-3)
     */
    private void handleFanSpeedUpdate(final int value) {
        try {
            logger.debug("handleFanSpeedUpdate called: value={}", value);
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), "ventilationSpeed");

            if (value >= 0 && value <= 3) {
                logger.debug("Updating ventilation speed channel to value={}", value);
                updateState(channelUID, new StringType(String.valueOf(value)));
                logger.debug("Ventilation speed channel updated successfully");
            } else {
                logger.warn("Invalid fan speed value: {} (out of range 0-3)", value);
                updateState(channelUID, UnDefType.UNDEF);
            }
        } catch (Exception e) {
            logger.error("Error updating ventilation speed channel: {}", e.getMessage(), e);
        }
    }

    /**
     * Discover the gateway UUID via UDP discovery message sent directly to the gateway host.
     *
     * @param hostname the hostname or IP address of the gateway
     * @param port the UDP port of the gateway
     * @return the discovered gateway UUID, or null if discovery fails
     */
    private @Nullable UUID discoverGatewayUuid(final String hostname, final int port) {
        try {
            logger.debug("Attempting to discover gateway UUID from {}:{}", hostname, port);
            InetAddress gatewayAddress = InetAddress.getByName(hostname);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000); // 5 second timeout for discovery

                // Send discovery message (same format as in ComfoConnectDiscoveryService)
                byte[] discoveryMessage = { 0x0a, 0x00 };
                DatagramPacket sendPacket = new DatagramPacket(discoveryMessage, discoveryMessage.length,
                        gatewayAddress, port);
                socket.send(sendPacket);

                // Receive response
                byte[] buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                // Parse discovery response
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
                        receivePacket.getLength());

                Zehnder.DiscoveryOperation operation = Zehnder.DiscoveryOperation.parseFrom(data);
                if (operation.hasSearchGatewayResponse()) {
                    Zehnder.SearchGatewayResponse response = operation.getSearchGatewayResponse();
                    byte[] uuidBytes = response.getUuid().toByteArray();
                    UUID uuid = bytesToUuid(uuidBytes);
                    logger.info("Gateway UUID discovered: {}", uuid);
                    return uuid;
                }
            }
        } catch (SocketTimeoutException e) {
            logger.debug("Gateway discovery timeout for {}:{}", hostname, port);
        } catch (Exception e) {
            logger.debug("Gateway discovery failed for {}:{} - {}", hostname, port, e.getMessage());
        }
        return null;
    }

    /**
     * Convert 16 bytes to a UUID.
     *
     * @param bytes the 16-byte UUID
     * @return the UUID
     */
    private UUID bytesToUuid(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes long");
        }
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xFF);
            least = (least << 8) | (bytes[8 + i] & 0xFF);
        }
        return new UUID(most, least);
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

    /**
     * Called when a channel is linked to an item.
     * Subscribes to the corresponding sensor if not already subscribed.
     *
     * @param channelUID the UID of the linked channel
     */
    @Override
    public void channelLinked(ChannelUID channelUID) {
        String channelId = channelUID.getId();
        int sensorId = getChannelSensorId(channelId);
        if (sensorId >= 0) {
            logger.debug("Channel {} linked, subscribing to sensor {}", channelId, sensorId);
            subscribeToSensorForChannel(sensorId);
        } else {
            logger.warn("Channel {} linked but no sensor mapping found", channelId);
        }
    }

    /**
     * Called when a channel is unlinked from an item.
     *
     * @param channelUID the UID of the unlinked channel
     */
    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        logger.debug("Channel {} unlinked", channelUID.getId());
        // Note: We don't unsubscribe because other channels might use the same sensor,
        // and the gateway doesn't provide an unsubscribe mechanism anyway.
        // Leaving the subscription active is harmless - the data just won't be processed.
    }

    /**
     * Subscribe to all sensors that have linked channels.
     * Called during bridge initialization to discover which channels are linked
     * and subscribe only to their corresponding sensors.
     */
    private void subscribeToLinkedChannels() {
        logger.debug("Discovering linked channels and subscribing to sensors");
        for (Channel channel : getThing().getChannels()) {
            if (isLinked(channel.getUID())) {
                String channelId = channel.getUID().getId();
                int sensorId = getChannelSensorId(channelId);
                if (sensorId >= 0) {
                    logger.debug("Channel {} is linked at startup, subscribing to sensor {}", channelId, sensorId);
                    subscribeToSensorForChannel(sensorId);
                }
            }
        }
    }

    /**
     * Get the sensor ID for a given channel ID.
     * Maps all 42 channel IDs to their corresponding sensor IDs.
     *
     * @param channelId the channel ID
     * @return the sensor ID, or -1 if unknown
     */
    private int getChannelSensorId(String channelId) {
        return switch (channelId) {
            // Phase 1: Fan-related sensors
            case "operatingMode" -> 1;
            case "ventilationSpeed" -> 65;
            case "supplyFanSpeed" -> 74;
            case "exhaustFanSpeed" -> 75;
            case "supplyFanSpeedSet" -> 76;
            case "exhaustFanSpeedSet" -> 77;
            case "supplyFanSpeedPercentage" -> 66;
            case "exhaustFanSpeedPercentage" -> 67;
            case "supplyFanSpeedPercentageSet" -> 68;
            case "exhaustFanSpeedPercentageSet" -> 69;
            case "bypassState" -> 81;
            case "preheaterState" -> 82;
            case "currentHumidity" -> 10;
            case "targetHumidity" -> 11;
            case "humidifierHumidity" -> 209;
            // Phase 2: Other basic sensors
            case "weekProfileActive" -> 12;
            case "globalAllergenMode" -> 32;
            case "ewtSpeed" -> 88;
            case "ewtPosition" -> 89;
            case "enthalpyState" -> 96;
            case "frostProtectionSpeed" -> 97;
            case "frostProtectionLoss" -> 98;
            case "frostProtectionTimeout" -> 99;
            case "hcePresent" -> 200;
            // Phase 3: Sensors with value corrections
            // Temperature sensors (divide by 10)
            case "outdoorTemperatureIn" -> 2;
            case "outdoorTemperatureOut" -> 3;
            case "indoorTemperatureIn" -> 4;
            case "indoorTemperatureOut" -> 5;
            case "ewtTemperature" -> 100;
            case "cookerTemperature" -> 101;
            case "heaterTemperature" -> 102;
            case "preHeaterTemperature" -> 103;
            case "indoorHumidity" -> 104;
            // Humidity sensors (no correction)
            case "exhaustHumidity" -> 13;
            case "indoorHumidity2" -> 14;
            case "exhaustHumidity2" -> 15;
            case "indoorHumidity3" -> 16;
            case "comfoSupplyHumidity" -> 105;
            // Boolean sensors
            case "t1SensorPresent" -> 17;
            case "t2SensorPresent" -> 18;
            case "t3SensorPresent" -> 21;
            // Mapping sensor
            case "temperatureUnit" -> 208;
            // Phase 4: Complex sensors
            case "airflowConstraints" -> 230;
            default -> -1;
        };
    }

    /**
     * Subscribe to a sensor by its ID.
     * Calls the appropriate subscription method on the protocol handler.
     *
     * @param sensorId the sensor ID to subscribe to
     */
    private void subscribeToSensorForChannel(int sensorId) {
        ComfoConnectProtocolHandler handler = protocolHandler;
        if (handler == null) {
            logger.warn("Cannot subscribe to sensor {}: protocol handler not initialized", sensorId);
            return;
        }

        try {
            switch (sensorId) {
                // Phase 1: Fan-related sensors
                case 1 -> handler.subscribeToOperatingModeSensor();
                case 65 -> handler.subscribeToFanSpeedSensor();
                case 74 -> handler.subscribeToSupplyFanSpeedSensor();
                case 75 -> handler.subscribeToExhaustFanSpeedSensor();
                case 76 -> handler.subscribeToSupplyFanSpeedSetSensor();
                case 77 -> handler.subscribeToExhaustFanSpeedSetSensor();
                case 66 -> handler.subscribeToSupplyFanSpeedPercentageSensor();
                case 67 -> handler.subscribeToExhaustFanSpeedPercentageSensor();
                case 68 -> handler.subscribeToSupplyFanSpeedPercentageSetSensor();
                case 69 -> handler.subscribeToExhaustFanSpeedPercentageSetSensor();
                case 81 -> handler.subscribeToBypassStateSensor();
                case 82 -> handler.subscribeToPreheaterStateSensor();
                case 10 -> handler.subscribeToCurrentHumiditySensor();
                case 11 -> handler.subscribeToTargetHumiditySensor();
                case 209 -> handler.subscribeToHumidifierHumiditySensor();
                // Phase 2: Other basic sensors
                case 12 -> handler.subscribeToWeekProfileActiveSensor();
                case 32 -> handler.subscribeToGlobalAllergenModeSensor();
                case 88 -> handler.subscribeToEwtSpeedSensor();
                case 89 -> handler.subscribeToEwtPositionSensor();
                case 96 -> handler.subscribeToEnthalpyStateSensor();
                case 97 -> handler.subscribeToFrostProtectionSpeedSensor();
                case 98 -> handler.subscribeToFrostProtectionLossSensor();
                case 99 -> handler.subscribeToFrostProtectionTimeoutSensor();
                case 200 -> handler.subscribeToHcePresentSensor();
                // Phase 3: Sensors with value corrections
                // Temperature sensors (divide by 10)
                case 2 -> handler.subscribeToOutdoorTemperatureInSensor();
                case 3 -> handler.subscribeToOutdoorTemperatureOutSensor();
                case 4 -> handler.subscribeToIndoorTemperatureInSensor();
                case 5 -> handler.subscribeToIndoorTemperatureOutSensor();
                case 100 -> handler.subscribeToEwtTemperatureSensor();
                case 101 -> handler.subscribeToCookerTemperatureSensor();
                case 102 -> handler.subscribeToHeaterTemperatureSensor();
                case 103 -> handler.subscribeToPreHeaterTemperatureSensor();
                case 104 -> handler.subscribeToIndoorHumiditySensor();
                // Humidity sensors (no correction)
                case 13 -> handler.subscribeToExhaustHumiditySensor();
                case 14 -> handler.subscribeToIndoorHumidity2Sensor();
                case 15 -> handler.subscribeToExhaustHumidity2Sensor();
                case 16 -> handler.subscribeToIndoorHumidity3Sensor();
                case 105 -> handler.subscribeToComfoSupplyHumiditySensor();
                // Boolean sensors
                case 17 -> handler.subscribeToT1SensorPresentSensor();
                case 18 -> handler.subscribeToT2SensorPresentSensor();
                case 21 -> handler.subscribeToT3SensorPresentSensor();
                // Mapping sensor
                case 208 -> handler.subscribeToTemperatureUnitSensor();
                // Phase 4: Complex sensors
                case 230 -> handler.subscribeToAirflowConstraintsSensor();
                default -> logger.debug("Unknown sensor: {}", sensorId);
            }
        } catch (Exception e) {
            logger.warn("Error subscribing to sensor {}: {}", sensorId, e.getMessage());
        }
    }
}
