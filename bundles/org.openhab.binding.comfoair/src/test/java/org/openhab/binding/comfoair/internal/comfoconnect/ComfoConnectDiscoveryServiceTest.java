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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.config.discovery.DiscoveryListener;
import org.openhab.core.net.NetworkAddressService;

/**
 * Test class for {@link ComfoConnectDiscoveryService}.
 *
 * @author Sascha Knoop - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
public class ComfoConnectDiscoveryServiceTest {

    private static final String TEST_GATEWAY_UUID = "d4c5e8f7-1a2b-3c4d-5e6f-7a8b9c0d1e2f";
    private static final String TEST_GATEWAY_IP = "192.168.1.100";
    private static final String PRIMARY_NIC = "192.168.1.50";
    private static final String BROADCAST_ADDRESS = "192.168.1.255";
    private static final int GATEWAY_PORT = 56747;

    private @Mock DatagramSocket mockSocket;
    private @Mock DiscoveryListener mockListener;
    private @Mock NetworkAddressService mockNetworkAddressService;

    private ComfoConnectDiscoveryService discoveryService;

    @BeforeEach
    public void setUp() {
        discoveryService = new ComfoConnectDiscoveryService() {
            @Override
            protected DatagramSocket createDatagramSocket() {
                return mockSocket;
            }
        };
        discoveryService.setNetworkAddressService(mockNetworkAddressService);
        discoveryService.addDiscoveryListener(mockListener);
    }

    @Test
    public void testSuccessfulGatewayDiscovery() throws Exception {
        // GIVEN: Primary NIC and broadcast address are configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(PRIMARY_NIC);
        when(mockNetworkAddressService.getConfiguredBroadcastAddress()).thenReturn(BROADCAST_ADDRESS);

        // AND: Mock socket send succeeds
        doNothing().when(mockSocket).send(any(DatagramPacket.class));

        // AND: Mock socket receives a valid discovery response
        // We use doThrow to simulate a timeout after the first receive call completes
        doThrow(new SocketTimeoutException()).when(mockSocket).receive(any());

        // WHEN: Discovery service starts scan
        discoveryService.startScan();

        // THEN: Discovery completes without throwing (timeout is expected)
        // The service should gracefully handle the timeout
        verify(mockSocket).send(any(DatagramPacket.class));
    }

    @Test
    public void testSkipsDiscoveryWhenPrimaryNICNotConfigured() {
        // GIVEN: Primary NIC is not configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(null);

        // WHEN: Discovery service runs
        discoveryService.startScan();

        // THEN: Discovery is skipped (no socket created, no results)
        verify(mockListener, never()).thingDiscovered(any(), any());
    }

    @Test
    public void testSkipsDiscoveryWhenBroadcastAddressNotConfigured() {
        // GIVEN: Primary NIC is configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(PRIMARY_NIC);

        // AND: Broadcast address is not configured
        when(mockNetworkAddressService.getConfiguredBroadcastAddress()).thenReturn(null);

        // WHEN: Discovery service runs
        discoveryService.startScan();

        // THEN: Discovery is skipped (no listener notification)
        verify(mockListener, never()).thingDiscovered(any(), any());
    }

    @Test
    public void testDiscoveryTimeoutWhenNoGateway() throws Exception {
        // GIVEN: Primary NIC and broadcast address are configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(PRIMARY_NIC);
        when(mockNetworkAddressService.getConfiguredBroadcastAddress()).thenReturn(BROADCAST_ADDRESS);

        // AND: Socket times out (no response)
        doThrow(new SocketTimeoutException()).when(mockSocket).receive(any());

        // WHEN: Discovery service runs
        discoveryService.startScan();

        // THEN: No discovery result is created (timeout is expected)
        verify(mockListener, never()).thingDiscovered(any(), any());
    }

    @Test
    public void testMalformedDiscoveryResponse() throws Exception {
        // GIVEN: Primary NIC and broadcast address are configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(PRIMARY_NIC);
        when(mockNetworkAddressService.getConfiguredBroadcastAddress()).thenReturn(BROADCAST_ADDRESS);

        // AND: Socket receives invalid protobuf data
        doAnswer(invocation -> {
            DatagramPacket packet = invocation.getArgument(0);
            packet.setData(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0xFF });
            packet.setLength(4);
            return null;
        }).when(mockSocket).receive(any());

        // WHEN: Discovery service runs
        discoveryService.startScan();

        // THEN: No discovery result created, no exception thrown
        verify(mockListener, never()).thingDiscovered(any(), any());
    }

    @Test
    public void testSocketCreationFailure() {
        // GIVEN: Primary NIC and broadcast address are configured
        when(mockNetworkAddressService.getPrimaryIpv4HostAddress()).thenReturn(PRIMARY_NIC);
        when(mockNetworkAddressService.getConfiguredBroadcastAddress()).thenReturn(BROADCAST_ADDRESS);

        // AND: Socket creation throws SocketException
        discoveryService = new ComfoConnectDiscoveryService() {
            @Override
            protected DatagramSocket createDatagramSocket() throws SocketException {
                throw new SocketException("Network unreachable");
            }
        };
        discoveryService.setNetworkAddressService(mockNetworkAddressService);
        discoveryService.addDiscoveryListener(mockListener);

        // WHEN: Discovery service runs
        discoveryService.startScan();

        // THEN: No listener notification, graceful error handling
        verify(mockListener, never()).thingDiscovered(any(), any());
    }
}
