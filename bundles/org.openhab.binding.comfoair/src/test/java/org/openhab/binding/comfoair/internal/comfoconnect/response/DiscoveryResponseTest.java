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
package org.openhab.binding.comfoair.internal.comfoconnect.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;
import com.zehnder.proto.Zehnder;

/**
 * Unit tests for {@link DiscoveryResponse}.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class DiscoveryResponseTest {

    private static final String TEST_UUID = "d4c5e8f7-1a2b-3c4d-5e6f-7a8b9c0d1e2f";
    private static final String TEST_IP = "192.168.1.100";

    @Test
    @DisplayName("Parse valid discovery response bytes successfully")
    public void testFromByteArrayWithValidResponse() {
        // GIVEN: Valid discovery response bytes
        byte[] responseBytes = createValidDiscoveryResponse(TEST_UUID, TEST_IP);

        // WHEN: Parsing the response
        DiscoveryResponse result = DiscoveryResponse.from(responseBytes);

        // THEN: Result is not null and contains correct data
        assertNotNull(result, "DiscoveryResponse should not be null for valid data");
        assertEquals(TEST_UUID, result.getUuid(), "UUID should match");
        assertEquals(TEST_IP, result.getResponseIp(), "IP address should match");
    }

    @Test
    @DisplayName("Return null when parsing invalid protobuf data")
    public void testFromByteArrayWithInvalidResponse() {
        // GIVEN: Invalid protobuf data
        byte[] invalidData = new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0x00, (byte) 0xFF };

        // WHEN: Parsing invalid data
        DiscoveryResponse result = DiscoveryResponse.from(invalidData);

        // THEN: Result is null
        assertNull(result, "DiscoveryResponse should be null for invalid data");
    }

    @Test
    @DisplayName("Return null when discovery operation has no SearchGatewayResponse")
    public void testFromByteArrayWithoutSearchGatewayResponse() {
        // GIVEN: Discovery operation without SearchGatewayResponse
        Zehnder.DiscoveryOperation operation = Zehnder.DiscoveryOperation.newBuilder().build();
        byte[] responseBytes = operation.toByteArray();

        // WHEN: Parsing the response
        DiscoveryResponse result = DiscoveryResponse.from(responseBytes);

        // THEN: Result is null
        assertNull(result, "DiscoveryResponse should be null when no SearchGatewayResponse is present");
    }

    @Test
    @DisplayName("Convert valid 16-byte UUID to string representation")
    public void testBytesToUuidWithValid16Bytes() {
        // GIVEN: Valid 16-byte UUID
        UUID uuid = UUID.fromString(TEST_UUID);
        byte[] uuidBytes = new byte[16];
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();

        for (int i = 0; i < 8; i++) {
            uuidBytes[i] = (byte) (most >> (8 * (7 - i)));
            uuidBytes[8 + i] = (byte) (least >> (8 * (7 - i)));
        }

        // WHEN: Converting bytes to UUID string
        String result = DiscoveryResponse.bytesToUuid(uuidBytes);

        // THEN: Result matches the original UUID
        assertEquals(TEST_UUID, result, "UUID string should match the original");
    }

    @Test
    @DisplayName("Throw exception when UUID byte array has invalid length")
    public void testBytesToUuidWithInvalidLength() {
        // GIVEN: Invalid byte array length
        byte[] invalidBytes = new byte[15];

        // WHEN/THEN: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> DiscoveryResponse.bytesToUuid(invalidBytes),
                "Should throw exception for invalid byte array length");
    }

    @Test
    @DisplayName("Verify DiscoveryResponse getters return correct values")
    public void testGetters() {
        // GIVEN: A valid DiscoveryResponse
        byte[] responseBytes = createValidDiscoveryResponse(TEST_UUID, TEST_IP);
        DiscoveryResponse response = DiscoveryResponse.from(responseBytes);

        // WHEN/THEN: Verify getters return correct values
        assertNotNull(response, "Response should not be null");
        assertEquals(TEST_UUID, response.getUuid(), "getUuid() should return correct UUID");
        assertEquals(TEST_IP, response.getResponseIp(), "getResponseIp() should return correct IP address");
    }

    /**
     * Create a valid DiscoveryOperation response with given UUID and IP address.
     */
    private byte[] createValidDiscoveryResponse(String uuid, String ipAddress) {
        UUID parsedUuid = UUID.fromString(uuid);
        byte[] uuidBytes = new byte[16];
        long most = parsedUuid.getMostSignificantBits();
        long least = parsedUuid.getLeastSignificantBits();

        for (int i = 0; i < 8; i++) {
            uuidBytes[i] = (byte) (most >> (8 * (7 - i)));
            uuidBytes[8 + i] = (byte) (least >> (8 * (7 - i)));
        }

        Zehnder.SearchGatewayResponse response = Zehnder.SearchGatewayResponse.newBuilder()
                .setUuid(ByteString.copyFrom(uuidBytes)).setIpaddress(ipAddress).setVersion(1).build();

        Zehnder.DiscoveryOperation operation = Zehnder.DiscoveryOperation.newBuilder()
                .setSearchGatewayResponse(response).build();

        return operation.toByteArray();
    }
}
