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
 * Represents a discovery response from a ComfoConnect gateway.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class DiscoveryResponse extends Response {
    private final String uuid;
    private final String responseIp;

    /**
     * Private constructor to enforce creation through the static factory method.
     *
     * @param uuid the gateway UUID
     * @param responseIp the gateway IP address
     */
    private DiscoveryResponse(String uuid, String responseIp) {
        this.uuid = uuid;
        this.responseIp = responseIp;
    }

    /**
     * Creates a DiscoveryResponse instance from raw byte data.
     *
     * @param data the raw discovery response data
     * @return a DiscoveryResponse instance, or null if the data is invalid or doesn't contain a SearchGatewayResponse
     */
    public static @Nullable DiscoveryResponse from(byte[] data) {
        try {
            com.zehnder.proto.Zehnder.DiscoveryOperation operation = com.zehnder.proto.Zehnder.DiscoveryOperation
                    .parseFrom(data);

            if (!operation.hasSearchGatewayResponse()) {
                return null;
            }

            com.zehnder.proto.Zehnder.SearchGatewayResponse response = operation.getSearchGatewayResponse();
            byte[] uuidBytes = response.getUuid().toByteArray();
            String uuid = bytesToUuid(uuidBytes);
            String responseIp = response.getIpaddress();

            return new DiscoveryResponse(uuid, responseIp);
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
    public String getResponseIp() {
        return responseIp;
    }
}
