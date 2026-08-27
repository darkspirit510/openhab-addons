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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.binding.comfoair.internal.comfoconnect.RequestExecutor;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.UuidConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.response.SearchGatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.zehnder.proto.Zehnder;

/**
 * Use case for gateway operations on the ComfoConnect gateway.
 * Handles discovery, listing registered apps, and app registration protocol requests.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class Gateway {
    private final Logger logger = LoggerFactory.getLogger(Gateway.class);
    private final UuidConverter uuidConverter = new UuidConverter();
    private final @Nullable RequestExecutor requestExecutor;

    /**
     * Create a new Gateway instance for protocol operations.
     *
     * @param requestExecutor executor that sends protocol requests and returns byte array responses
     */
    public Gateway(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    /**
     * Create a new Gateway instance for discovery operations.
     * This constructor is used when only discovery functionality is needed.
     */
    public Gateway() {
        this.requestExecutor = null;
    }

    /**
     * Discover the gateway UUID for a given hostname via UDP.
     *
     * @param hostname the hostname to discover
     * @return the discovered gateway UUID, or null if discovery fails
     */
    public @Nullable UUID discoverUuid(final String hostname) {
        try {
            logger.debug("Attempting to discover gateway UUID from {}:{}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
            InetAddress gatewayAddress = InetAddress.getByName(hostname);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(5000); // 5 second timeout for discovery

                // Send discovery message (same format as in ComfoConnectDiscoveryService)
                byte[] discoveryMessage = { 0x0a, 0x00 };
                DatagramPacket sendPacket = new DatagramPacket(discoveryMessage, discoveryMessage.length,
                        gatewayAddress, ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
                socket.send(sendPacket);

                // Receive response
                byte[] buffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                // Parse discovery response
                byte[] data = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0,
                        receivePacket.getLength());

                SearchGatewayResponse searchResponse = SearchGatewayResponse.from(data);

                if (searchResponse != null) {
                    UUID uuid = UUID.fromString(searchResponse.getUuid());
                    logger.info("Gateway UUID discovered: {}", uuid);
                    return uuid;
                }
            }
        } catch (SocketTimeoutException e) {
            logger.debug("Gateway discovery timeout for {}:{}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT);
        } catch (Exception e) {
            logger.debug("Gateway discovery failed for {}:{} - {}", hostname,
                    ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_PORT, e.getMessage());
        }

        return null;
    }

    /**
     * Execute the use case: list all registered app UUIDs.
     *
     * @return list of registered app UUIDs
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted during execution
     */
    public List<UUID> registeredApps() throws IOException, InterruptedException {
        if (requestExecutor == null) {
            throw new IllegalStateException("RequestExecutor is required for this operation");
        }
        Zehnder.ListRegisteredAppsRequest request = Zehnder.ListRegisteredAppsRequest.newBuilder().build();

        try {
            Zehnder.ListRegisteredAppsConfirm confirm = Zehnder.ListRegisteredAppsConfirm
                    .parseFrom(requestExecutor.execute(request));

            return confirm.getAppsList().stream().map(app -> uuidConverter.fromBytes(app.getUuid().toByteArray()))
                    .toList();
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Failed to parse registered apps response", e);
        }
    }

    /**
     * Register the app with the gateway using PIN.
     *
     * @param clientUuid the UUID to register
     * @param pinCode the PIN for gateway registration
     * @throws IOException if registration fails (e.g., invalid PIN)
     * @throws InterruptedException if interrupted
     */
    public void registerApp(UUID clientUuid, int pinCode) throws IOException, InterruptedException {
        if (requestExecutor == null) {
            throw new IllegalStateException("RequestExecutor is required for this operation");
        }
        logger.debug("Registering app with gateway");

        Zehnder.RegisterAppRequest request = Zehnder.RegisterAppRequest.newBuilder()
                .setUuid(ByteString.copyFrom(uuidConverter.toBytes(clientUuid))).setPin(pinCode)
                .setDevicename("openHAB").build();

        requestExecutor.execute(request);
    }

    /**
     * Ensure the app is registered with the gateway. Checks if already registered, and if not,
     * attempts registration.
     *
     * @param clientUuid the client UUID to check/register
     * @param pinCode the PIN for gateway registration (only used if registration is needed)
     * @throws IOException if registration fails or if checking registered apps fails
     * @throws InterruptedException if interrupted
     */
    public void ensureRegistration(UUID clientUuid, int pinCode) throws IOException, InterruptedException {
        logger.debug("Checking if app registration is needed");

        try {
            // Get list of already registered apps
            if (registeredApps().contains(clientUuid)) {
                logger.debug("App already registered with UUID: {}", clientUuid);
                return;
            }

            logger.debug("App not yet registered, attempting registration");
            registerApp(clientUuid, pinCode);
        } catch (IOException e) {
            // If listing registered apps fails, attempt registration as fallback
            logger.debug("Failed to list registered apps ({}), attempting registration anyway", e.getMessage());
            registerApp(clientUuid, pinCode); // Will throw IOException if fails
        }
    }
}
