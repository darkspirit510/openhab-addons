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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Interface for managing TCP socket connections for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface SocketManager {

    /**
     * Connect to the gateway.
     *
     * @throws IOException if connection fails
     */
    void connect() throws IOException;

    /**
     * Disconnect from the gateway.
     */
    void disconnect();

    /**
     * Cleanup resources.
     */
    void cleanup();

    /**
     * Send a message to the gateway.
     *
     * @param message the message to send
     * @throws IOException if send fails
     */
    void sendMessage(byte[] message) throws IOException;

    /**
     * Get the input stream for reading from the socket.
     *
     * @return the input stream
     */
    @Nullable
    DataInputStream getInputStream();

    /**
     * Get the output stream for writing to the socket.
     *
     * @return the output stream
     */
    @Nullable
    DataOutputStream getOutputStream();

    /**
     * Get the underlying socket.
     *
     * @return the socket
     */
    @Nullable
    Socket getSocket();

    /**
     * Check if the socket is connected.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();
}
