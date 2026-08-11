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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Configuration class for ComfoConnect TCP bridge (newer Q-series devices).
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectConfiguration {

    /**
     * Gateway hostname or IP address.
     */
    public @Nullable String hostname;

    /**
     * Gateway TCP port (typically 56747).
     */
    public int port = 56747;

    /**
     * PIN for authentication.
     */
    public @Nullable String pin;

    /**
     * Client UUID (optional, will be auto-generated if not provided).
     */
    public @Nullable String clientUuid;

    /**
     * Gateway UUID (should be set from discovery result).
     */
    public @Nullable String gatewayUuid;

    /**
     * Device refresh interval in seconds.
     */
    public int refreshInterval = 30;

    /**
     * Whether to automatically take over an existing session if another app is logged in.
     */
    public boolean autoTakeover = false;

    /**
     * Timeout in seconds for sending commands to the device.
     */
    public int defaultCommandTimeout = 3;
}
