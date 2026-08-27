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
package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface for managing bypass state polling for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface BypassStateManager {

    /**
     * Start polling for bypass state.
     */
    void startBypassStatePolling();

    /**
     * Stop polling for bypass state.
     */
    void stopBypassStatePolling();
}
