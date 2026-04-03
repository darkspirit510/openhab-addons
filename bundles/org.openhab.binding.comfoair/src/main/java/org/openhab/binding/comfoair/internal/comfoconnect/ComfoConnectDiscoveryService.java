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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * Discovery service for ComfoConnect gateways.
 *
 * Discovers ComfoConnect LAN gateways via UDP broadcast on the local network.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectDiscoveryService.class);

    private static final int DISCOVERY_TIMEOUT_SECONDS = 3;
    private static final int GATEWAY_PORT = 56747;
    private static final byte[] DISCOVERY_MESSAGE = { 0x0a, 0x00 };
    private static final Set<ThingTypeUID> DISCOVERABLE_THINGS = Collections
            .unmodifiableSet(Set.of(ComfoAirBindingConstants.THING_TYPE_COMFOCONNECT_LAN_BRIDGE));

    private @Nullable NetworkAddressService networkAddressService;

    @Reference
    protected void setNetworkAddressService(final NetworkAddressService networkAddressService) {
        this.networkAddressService = networkAddressService;
    }

    protected void unsetNetworkAddressService(final NetworkAddressService networkAddressService) {
        this.networkAddressService = null;
    }

    /**
     * Create a new discovery service.
     */
    public ComfoConnectDiscoveryService() {
        super(DISCOVERABLE_THINGS, DISCOVERY_TIMEOUT_SECONDS);
    }

    @Override
    protected void startBackgroundDiscovery() {
        discoverGateway();
    }

    @Override
    protected void stopBackgroundDiscovery() {
        super.stopBackgroundDiscovery();
    }

    @Override
    protected void startScan() {
        discoverGateway();
    }

    @Override
    public void stopScan() {
        logger.debug("Stopping ComfoConnect discovery scan");
        super.stopScan();
    }

    /**
     * Discover ComfoConnect gateways via UDP broadcast.
     */
    private void discoverGateway() {
        NetworkAddressService nas = networkAddressService;
        if (nas == null) {
            logger.warn("NetworkAddressService not available, skipping ComfoConnect gateway discovery");
            return;
        }

        String primaryNic = nas.getPrimaryIpv4HostAddress();
        if (primaryNic == null || primaryNic.isEmpty()) {
            logger.warn(
                    "Primary network interface not configured in openHAB. Please configure the primary network interface in openHAB settings to enable ComfoConnect gateway discovery.");
            return;
        }

        String broadcastAddress = nas.getConfiguredBroadcastAddress();
        if (broadcastAddress == null || broadcastAddress.isEmpty()) {
            logger.warn(
                    "Broadcast address not configured in openHAB. Please configure network settings to enable ComfoConnect gateway discovery. You can manually add the gateway by specifying its IP address.");
            return;
        }

        try (DatagramSocket socket = createDatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT_SECONDS * 1000);

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            logger.debug("Sending ComfoConnect discovery broadcast to {}:{}", broadcastAddress, GATEWAY_PORT);
            DatagramPacket sendPacket = new DatagramPacket(DISCOVERY_MESSAGE, DISCOVERY_MESSAGE.length,
                    java.net.InetAddress.getByName(broadcastAddress), GATEWAY_PORT);
            socket.send(sendPacket);

            listenForResponse(socket, packet);
        } catch (SocketTimeoutException e) {
            logger.debug("ComfoConnect gateway discovery timeout (expected if no gateway on network)");
        } catch (Exception e) {
            logger.warn("Error during ComfoConnect gateway discovery: {}", e.getMessage());
        }
    }

    /**
     * Listen for and process discovery responses.
     *
     * @param socket the datagram socket
     * @param packet the packet to receive data into
     */
    private void listenForResponse(DatagramSocket socket, DatagramPacket packet) {
        try {
            socket.receive(packet);

            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

            parseDiscoveryResponse(data, packet.getAddress().getHostAddress());
        } catch (SocketTimeoutException e) {
            logger.debug("ComfoConnect discovery socket timeout");
        } catch (IOException e) {
            logger.warn("Error receiving ComfoConnect discovery response: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Error parsing ComfoConnect discovery response: {}", e.getMessage());
        }
    }

    /**
     * Parse a discovery response and create a DiscoveryResult.
     *
     * @param data the raw response data
     * @param ipAddress the IP address of the responding gateway
     */
    private void parseDiscoveryResponse(byte[] data, String ipAddress) {
        try {
            Zehnder.DiscoveryOperation operation = Zehnder.DiscoveryOperation.parseFrom(data);

            if (!operation.hasSearchGatewayResponse()) {
                logger.warn("Received discovery response without SearchGatewayResponse");
                return;
            }

            Zehnder.SearchGatewayResponse response = operation.getSearchGatewayResponse();
            byte[] uuidBytes = response.getUuid().toByteArray();
            String uuid = bytesToUuid(uuidBytes);
            String responseIp = response.getIpaddress();

            logger.debug("ComfoConnect gateway discovered: {} at {}", uuid, responseIp);
            createDiscoveryResult(uuid, responseIp);
        } catch (Exception e) {
            logger.warn("Error parsing ComfoConnect discovery response: {}", e.getMessage());
        }
    }

    /**
     * Convert 16 bytes to a UUID string.
     *
     * @param bytes the 16-byte UUID
     * @return the UUID as a string
     */
    private String bytesToUuid(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID bytes must be 16 bytes long");
        }
        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[i] & 0xFF);
            least = (least << 8) | (bytes[8 + i] & 0xFF);
        }
        java.util.UUID uuid = new java.util.UUID(most, least);
        return uuid.toString();
    }

    /**
     * Create and post a DiscoveryResult for a discovered gateway.
     *
     * @param uuid the gateway UUID
     * @param ipAddress the gateway IP address
     */
    private void createDiscoveryResult(String uuid, String ipAddress) {
        ThingTypeUID thingTypeUID = ComfoAirBindingConstants.THING_TYPE_COMFOCONNECT_LAN_BRIDGE;
        ThingUID thingUID = new ThingUID(thingTypeUID, uuid);

        Map<String, Object> properties = new HashMap<>();
        properties.put("ipAddress", ipAddress);
        properties.put("uuid", uuid);

        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withLabel("ComfoConnect Gateway (" + ipAddress + ")").build());
    }

    /**
     * Create a DatagramSocket for discovery. Can be overridden for testing.
     *
     * @return a new DatagramSocket
     * @throws SocketException if socket creation fails
     */
    protected DatagramSocket createDatagramSocket() throws SocketException {
        return new DatagramSocket();
    }
}
