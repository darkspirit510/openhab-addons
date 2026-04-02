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
 * Configuration class for ComfoConnect devices (individual nodes).
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectDeviceConfiguration {

    /**
     * Node ID of this device (assigned by gateway during discovery).
     */
    public int nodeId;

    /**
     * Device name/label.
     */
    public @Nullable String deviceName;

    /**
     * Device refresh interval in seconds.
     */
    public int refreshInterval = 30;
}
