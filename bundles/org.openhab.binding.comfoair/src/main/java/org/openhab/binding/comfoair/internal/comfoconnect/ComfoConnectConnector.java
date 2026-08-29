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
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.component.MessageQueue;
import org.openhab.binding.comfoair.internal.comfoconnect.component.ReaderThread;
import org.openhab.binding.comfoair.internal.comfoconnect.component.SocketWrapper;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.HexConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ProtobufFramer;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP socket-based connector for ComfoConnect protocol (newer Q-series devices).
 *
 * Manages:
 * - TCP socket connection to gateway
 * - Asynchronous message reading via reader thread
 * - Socket lifecycle with responsive shutdown via interrupt
 * - Asynchronous message handling via BlockingQueue
 * - Single reader thread with responsive shutdown via interrupt() + join()
 * - Message framing and protocol handling
 * - Connection lifecycle management
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectConnector {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectConnector.class);

    protected static final int DEFAULT_QUEUE_CAPACITY = 100;

    private final String hostname;
    private final int port;
    private final UUID clientUuid;

    private final SocketWrapper socketManager;
    private final ProtobufFramer framer;
    private final HexConverter hexConverter = new HexConverter();
    private @Nullable MessageQueue messageQueue;
    private @Nullable ReaderThread readerThread;

    protected volatile boolean isConnected = false;
    protected volatile boolean isShutdown = false;

    /**
     * Create a new TCP connector.
     *
     * @param hostname the gateway hostname or IP address
     * @param port the gateway TCP port (default 56747)
     * @param clientUuid the client UUID (random, use for identification)
     * @param gatewayUuid the gateway UUID
     */
    public ComfoConnectConnector(final String hostname, final int port, final UUID clientUuid, final UUID gatewayUuid) {
        this(hostname, port, clientUuid, gatewayUuid, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Create a new TCP connector with custom queue capacity.
     *
     * @param hostname the gateway hostname or IP address
     * @param port the gateway TCP port (default 56747)
     * @param clientUuid the client UUID (random, use for identification)
     * @param gatewayUuid the gateway UUID
     * @param queueCapacity the message queue capacity
     */
    public ComfoConnectConnector(final String hostname, final int port, final UUID clientUuid, final UUID gatewayUuid,
            final int queueCapacity) {
        this.hostname = hostname;
        this.port = port;
        this.clientUuid = clientUuid;
        this.framer = new ProtobufFramer(clientUuid, gatewayUuid);
        this.socketManager = new SocketWrapper(hostname, port);
    }

    /**
     * Set the Messages instance for this connector.
     * This must be called before connect() to enable message queuing and consumption.
     *
     * @param messageQueue the Messages instance to use
     */
    public void setMessages(final @Nullable MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    /**
     * Establish connection to the ComfoConnect gateway.
     * This method is responsible for:
     * - Connecting to the physical transport (socket)
     * - Starting the reader thread
     * - Performing any necessary handshaking or authentication
     *
     * @throws IOException if connection fails
     */
    public void connect() throws IOException {
        logger.info("Connecting to ComfoConnect gateway at {}:{}", hostname, port);

        try {
            socketManager.connect();

            // Initialize reader thread manager with the new input stream
            if (messageQueue != null) {
                readerThread = new ReaderThread(socketManager.getInputStream(), messageQueue);
                readerThread.start();
            }

            isConnected = true;
            logger.info("Connected to ComfoConnect gateway");
        } catch (IOException e) {
            logger.error("Failed to connect to ComfoConnect gateway: {}", e.getMessage());
            isConnected = false;
            socketManager.cleanup();
            throw e;
        }
    }

    /**
     * Gracefully close the connection.
     * This method is responsible for:
     * - Stopping the reader thread
     * - Closing any underlying resources (sockets, streams)
     */
    public void disconnect() {
        logger.info("Disconnecting from ComfoConnect gateway");
        isShutdown = true;

        if (messageQueue != null) {
            messageQueue.setShutdown(true);
        }

        if (this.readerThread != null) {
            this.readerThread.setConnected(false);
            this.readerThread.stopReaderThread();
        }

        socketManager.disconnect();
        isConnected = false;
        logger.info("Disconnected from ComfoConnect gateway");
    }

    /**
     * Send a message (raw bytes or protobuf) to the gateway.
     *
     * @param message the message to send
     * @throws IOException if send fails
     */
    public void sendMessage(final byte[] message) throws IOException {
        if (messageQueue != null) {
            messageQueue.sendMessage(message);
        } else {
            // Fallback to direct socket send if messages not set
            socketManager.sendMessage(message);
        }
    }

    /**
     * Send an RPDO request to subscribe to a sensor.
     *
     * @param pdid the PDO ID of the sensor
     * @param type the sensor data type
     * @throws IOException if send fails
     */
    public void sendRpdoRequest(final int pdid, final int type) throws IOException {
        if (messageQueue != null) {
            messageQueue.sendRpdoRequest(pdid, type);
        }
        Sensors.findById(pdid).ifPresentOrElse(sensor -> logger.info("RPDO request sent for sensor {}", sensor),
                () -> logger.info("RPDO request sent for sensor ??? ({})", pdid));
    }

    /**
     * Send an RPDO request to unsubscribe from a sensor.
     * According to the protocol, sending a CnRpdoRequest without the type field
     * will delete a previously registered RPDO with the given PDID.
     *
     * @param pdid the PDO ID of the sensor to unsubscribe from
     * @throws IOException if send fails
     */
    public void sendRpdoUnsubscribe(final int pdid) throws IOException {
        if (messageQueue != null) {
            messageQueue.sendRpdoUnsubscribe(pdid);
        }
        Sensors.findById(pdid).ifPresentOrElse(sensor -> logger.info("RPDO unsubscribe sent for sensor {}", sensor),
                () -> logger.info("RPDO unsubscribe sent for sensor ??? ({})", pdid));
    }

    /**
     * Send an RMI request to the gateway.
     *
     * @param nodeId the ComfoNet node ID (typically 1 for ventilation unit)
     * @param rmiMessage the raw RMI message bytes (e.g., 0x83, UNIT, SUBUNIT, PROPERTY)
     * @throws IOException if send fails
     */
    public void sendRmiRequest(final int nodeId, final byte[] rmiMessage) throws IOException {
        if (messageQueue != null) {
            messageQueue.sendRmiRequest(nodeId, rmiMessage);
        }
        logger.info("RMI request sent for node {}: {}", nodeId, new HexConverter().toHex(rmiMessage));
    }

    public ProtobufFramer getFramer() {
        return framer;
    }

    public UUID clientUuid() {
        return clientUuid;
    }

    /**
     * Get next message from the queue (blocking).
     * Allows consumers to receive messages in a thread-safe manner.
     *
     * @return the next message, or null if interrupted
     */
    public byte @org.eclipse.jdt.annotation.Nullable [] getNextMessage() {
        if (messageQueue != null) {
            return messageQueue.getNextMessage();
        }
        return null;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
