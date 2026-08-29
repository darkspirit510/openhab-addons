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
package org.openhab.binding.comfoair.internal.comfoconnect.component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages socket lifecycle and I/O operations for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SocketWrapper {

    private final Logger logger = LoggerFactory.getLogger(SocketWrapper.class);

    private static final int BLOCKING_READ_TIMEOUT = 0;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String hostname;
    private final int port;

    private @Nullable Socket socket;
    private @Nullable DataInputStream inputStream;
    private @Nullable DataOutputStream outputStream;

    private volatile boolean isConnected = false;

    /**
     * Create a new socket manager.
     *
     * @param hostname the gateway hostname or IP address
     * @param port the gateway TCP port
     */
    public SocketWrapper(final String hostname, final int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public void connect() throws IOException {
        logger.info("Connecting to ComfoConnect gateway at {}:{}", hostname, port);

        try {
            this.socket = createSocket();
            this.inputStream = new DataInputStream(this.socket.getInputStream());
            this.outputStream = new DataOutputStream(this.socket.getOutputStream());
            this.isConnected = true;

            logger.info("Connected to ComfoConnect gateway");
        } catch (IOException e) {
            logger.error("Failed to connect to ComfoConnect gateway: {}", e.getMessage());
            isConnected = false;
            cleanup();
            throw e;
        }
    }

    private @NonNull Socket createSocket() throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(BLOCKING_READ_TIMEOUT);
        socket.connect(new InetSocketAddress(hostname, port), CONNECT_TIMEOUT_MS);
        return socket;
    }

    public void disconnect() {
        logger.info("Disconnecting from ComfoConnect gateway");
        cleanup();
        isConnected = false;
        logger.info("Disconnected from ComfoConnect gateway");
    }

    public void cleanup() {
        if (this.inputStream != null) {
            try {
                this.inputStream.close();
            } catch (IOException e) {
                logger.debug("Error closing input stream: {}", e.getMessage());
            }
        }

        if (this.outputStream != null) {
            try {
                this.outputStream.close();
            } catch (IOException e) {
                logger.debug("Error closing output stream: {}", e.getMessage());
            }
        }

        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                logger.debug("Error closing socket: {}", e.getMessage());
            }
        }

        this.socket = null;
        this.inputStream = null;
        this.outputStream = null;
    }

    public void sendMessage(final byte[] message) throws IOException {
        DataOutputStream out = this.outputStream;
        if (out == null || socket == null || !isConnected) {
            throw new IOException("Not connected to gateway");
        }

        synchronized (out) {
            try {
                out.write(message);
                out.flush();
                logger.debug("Sent {} bytes to gateway", message.length);
            } catch (IOException e) {
                logger.error("Error sending message: {}", e.getMessage());
                isConnected = false;
                cleanup();
                throw e;
            }
        }
    }

    public @Nullable DataInputStream getInputStream() {
        return inputStream;
    }
}
