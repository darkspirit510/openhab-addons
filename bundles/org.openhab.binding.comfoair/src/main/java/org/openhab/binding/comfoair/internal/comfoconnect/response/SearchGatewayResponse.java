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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Represents a SearchGatewayResponse from a ComfoConnect gateway discovery operation.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SearchGatewayResponse extends Response {
    private final String uuid;
    private final String ipAddress;

    /**
     * Private constructor to enforce creation through the static factory method.
     *
     * @param uuid the gateway UUID
     * @param ipAddress the gateway IP address
     */
    private SearchGatewayResponse(String uuid, String ipAddress) {
        this.uuid = uuid;
        this.ipAddress = ipAddress;
    }

    /**
     * Parses a SearchGatewayResponse from raw byte data.
     *
     * @param data the raw discovery response data
     * @return a SearchGatewayResponse instance, or null if the data is invalid or doesn't contain a
     *         SearchGatewayResponse
     */
    public static @Nullable SearchGatewayResponse from(byte[] data) {
        try {
            com.zehnder.proto.Zehnder.DiscoveryOperation operation = com.zehnder.proto.Zehnder.DiscoveryOperation
                    .parseFrom(data);

            if (!operation.hasSearchGatewayResponse()) {
                return null;
            }

            com.zehnder.proto.Zehnder.SearchGatewayResponse response = operation.getSearchGatewayResponse();
            byte[] uuidBytes = response.getUuid().toByteArray();
            String uuid = bytesToUuid(uuidBytes);
            String ipAddress = response.getIpaddress();

            return new SearchGatewayResponse(uuid, ipAddress);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the gateway UUID.
     *
     * @return the UUID string
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Gets the gateway IP address.
     *
     * @return the IP address string
     */
    public String getIpAddress() {
        return ipAddress;
    }
}
