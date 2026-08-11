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

import com.google.protobuf.MessageLite;

/**
 * Functional interface for executing protocol requests that can throw checked exceptions.
 */
@FunctionalInterface
public interface RequestExecutor {
    /**
     * Execute a protocol request.
     *
     * @param request the request message
     * @return the response as byte array
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted during execution
     */
    byte[] execute(MessageLite request) throws IOException, InterruptedException;
}
