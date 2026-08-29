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
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the reader thread lifecycle for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ReaderThread {

    private final Logger logger = LoggerFactory.getLogger(ReaderThread.class);

    private static final int SOCKET_TIMEOUT_MS = 0; // No timeout - use blocking reads instead

    private final @Nullable DataInputStream inputStream;
    private final @Nullable MessageQueue messageQueue;

    private @Nullable Thread readerThread;
    private final Object readerThreadLock = new Object();
    private volatile boolean isConnected = false;

    /**
     * Create a new reader thread manager.
     *
     * @param inputStream the input stream to read from
     * @param messageQueue the Messages instance for queueing messages
     */
    public ReaderThread(final @Nullable DataInputStream inputStream, final @Nullable MessageQueue messageQueue) {
        this.inputStream = inputStream;
        this.messageQueue = messageQueue;
    }

    public void start() {
        synchronized (readerThreadLock) {
            Thread thread = readerThread;
            if (thread != null && thread.isAlive()) {
                logger.warn("Reader thread already running, stopping previous one");
                thread.interrupt();

                try {
                    thread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            Thread newThread = new Thread(this::readLoop, "ComfoConnect-Reader");
            newThread.setDaemon(true);
            newThread.start();
            readerThread = newThread;
            logger.debug("Reader thread started: {}", newThread.getName());

            this.isConnected = true;
        }
    }

    public void stopReaderThread() {
        synchronized (readerThreadLock) {
            Thread thread = readerThread;
            if (thread != null && thread.isAlive()) {
                logger.debug("Stopping reader thread");
                thread.interrupt();

                try {
                    thread.join(2000);

                    if (thread.isAlive()) {
                        logger.warn("Reader thread did not terminate within timeout");
                    }
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while stopping reader thread");
                    Thread.currentThread().interrupt();
                }

                readerThread = null;
            }
        }
    }

    public void setConnected(final boolean connected) {
        this.isConnected = connected;
    }

    /**
     * Main reader loop running in dedicated thread.
     * Reads complete frames from the socket and queues them for consumers.
     */
    private void readLoop() {
        DataInputStream in = this.inputStream;
        if (in == null) {
            logger.error("Input stream not initialized");
            return;
        }

        logger.info("Reader loop started, waiting for messages from gateway");
        try {
            while (!Thread.currentThread().isInterrupted() && isConnected) {
                try {
                    logger.debug("Attempting to read next message from gateway...");
                    int totalLength = in.readInt();

                    if (totalLength < 0 || totalLength > 65536) {
                        logger.warn("Invalid frame length: {}", totalLength);
                        break;
                    }

                    byte[] frameData = new byte[4 + totalLength];
                    ByteBuffer lengthBuffer = ByteBuffer.wrap(frameData, 0, 4);
                    lengthBuffer.putInt(totalLength);

                    in.readFully(frameData, 4, totalLength);

                    if (!messageQueue.queueMessage(frameData)) {
                        logger.warn("Message queue full, dropping message");
                    }

                    logger.trace("Received {} bytes", frameData.length);

                } catch (SocketTimeoutException e) {
                    // Socket timeout is normal - just continue waiting for data
                    logger.debug("Socket timeout while reading ({}ms), retrying...", SOCKET_TIMEOUT_MS);
                    continue;
                } catch (SocketException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.debug("Reader interrupted (SocketException): {}", e.getMessage());
                        break;
                    } else {
                        logger.error("Socket error: {}", e.getMessage());
                        isConnected = false;
                        break;
                    }
                }
            }
        } catch (EOFException e) {
            logger.info("Gateway closed connection");
            isConnected = false;
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                logger.error("I/O error in reader loop: {}", e.getMessage());
                isConnected = false;
            } else {
                logger.debug("Reader loop interrupted");
            }
        } finally {
            logger.debug("Reader loop exiting");
        }
    }
}
