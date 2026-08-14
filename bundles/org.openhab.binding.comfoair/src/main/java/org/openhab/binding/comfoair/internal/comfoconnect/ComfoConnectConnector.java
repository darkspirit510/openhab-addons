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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for ComfoConnect connector implementations supporting multiple connection types
 * (TCP socket-based for newer Q-series devices).
 *
 * Provides:
 * - Asynchronous message handling via BlockingQueue
 * - Single reader thread with responsive shutdown via interrupt() + join()
 * - Message framing and protocol handling abstraction
 * - Connection lifecycle management
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public abstract class ComfoConnectConnector {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectConnector.class);

    protected static final int SOCKET_TIMEOUT_MS = 0; // No timeout - use blocking reads instead
    protected static final int DEFAULT_QUEUE_CAPACITY = 100;

    protected final BlockingQueue<byte[]> messageQueue;
    protected volatile boolean isConnected = false;
    protected volatile boolean isShutdown = false;

    protected @Nullable Thread readerThread;
    protected final Object readerThreadLock = new Object();

    protected ComfoConnectConnector() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    protected ComfoConnectConnector(final int queueCapacity) {
        this.messageQueue = new LinkedBlockingQueue<>(queueCapacity);
    }

    /**
     * Establish connection to the ComfoConnect gateway.
     * This method is responsible for:
     * - Connecting to the physical transport (socket, serial, etc.)
     * - Starting the reader thread
     * - Performing any necessary handshaking or authentication
     *
     * @throws IOException if connection fails
     */
    public abstract void connect() throws IOException;

    /**
     * Gracefully close the connection.
     * This method is responsible for:
     * - Stopping the reader thread
     * - Closing any underlying resources (sockets, streams)
     */
    public abstract void disconnect();

    /**
     * Send a message (raw bytes or protobuf) to the gateway.
     *
     * @param message the message to send
     * @throws IOException if send fails
     */
    public abstract void sendMessage(byte[] message) throws IOException;

    /**
     * Send an RPDO request to subscribe to a sensor.
     *
     * @param pdid the PDO ID of the sensor
     * @param type the sensor data type
     * @throws IOException if send fails
     */
    public abstract void sendRpdoRequest(int pdid, int type) throws IOException;

    /**
     * Send an RPDO request to unsubscribe from a sensor.
     * According to the protocol, sending a CnRpdoRequest without the type field
     * will delete a previously registered RPDO with the given PDID.
     *
     * @param pdid the PDO ID of the sensor to unsubscribe from
     * @throws IOException if send fails
     */
    public abstract void sendRpdoUnsubscribe(int pdid) throws IOException;

    /**
     * Send an RMI request to the gateway.
     *
     * @param nodeId the ComfoNet node ID (typically 1 for ventilation unit)
     * @param rmiMessage the raw RMI message bytes (e.g., 0x83, UNIT, SUBUNIT, PROPERTY)
     * @throws IOException if send fails
     */
    public abstract void sendRmiRequest(int nodeId, byte[] rmiMessage) throws IOException;

    public abstract ProtobufFramer getFramer();

    public abstract java.util.UUID getClientUuid();

    /**
     * Get next message from the queue (blocking).
     * Allows consumers to receive messages in a thread-safe manner.
     *
     * @return the next message, or null if interrupted
     */
    public byte @org.eclipse.jdt.annotation.Nullable [] getNextMessage() {
        try {
            byte[] message = messageQueue.take();
            logger.info("Retrieved message from queue: {} bytes, first byte: 0x{}",
                    message != null ? message.length : 0,
                    message != null && message.length > 0 ? String.format("%02X", message[0]) : "N/A");
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("getNextMessage interrupted");

            return null;
        }
    }

    public byte @org.eclipse.jdt.annotation.Nullable [] pollMessage(final long timeoutMs) {
        try {
            return messageQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("pollMessage interrupted");

            return null;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Start the reader thread with the given runnable.
     * The thread will be interrupted via interrupt() on disconnect.
     *
     * @param reader the runnable that implements the message reading loop
     * @param threadName the name for the reader thread (for logging)
     */
    protected void startReaderThread(final Runnable reader, final String threadName) {
        synchronized (readerThreadLock) {
            if (readerThread != null && readerThread.isAlive()) {
                logger.warn("Reader thread already running, stopping previous one");
                readerThread.interrupt();

                try {
                    readerThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            readerThread = new Thread(reader, threadName);
            readerThread.setDaemon(true);
            readerThread.start();
            logger.debug("Reader thread started: {}", threadName);
        }
    }

    /**
     * Stop the reader thread gracefully via interrupt + join.
     * This is safe to call multiple times.
     */
    protected void stopReaderThread() {
        synchronized (readerThreadLock) {
            if (readerThread != null && readerThread.isAlive()) {
                logger.debug("Stopping reader thread");
                readerThread.interrupt();

                try {
                    readerThread.join(2000);

                    if (readerThread.isAlive()) {
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

    /**
     * Queue a message for delivery to consumers.
     * Should be called by subclasses from the reader thread when messages arrive.
     *
     * @param message the message to queue
     * @return true if queued successfully, false if queue is full
     */
    protected boolean queueMessage(final byte[] message) {
        if (isShutdown) {
            return false;
        }

        return messageQueue.offer(message);
    }

    /**
     * Clear all pending messages in the queue.
     */
    protected void clearQueue() {
        messageQueue.clear();
    }
}
