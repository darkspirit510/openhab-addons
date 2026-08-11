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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;

/**
 * TCP socket-based connector for ComfoConnect protocol (newer Q-series devices).
 *
 * Manages:
 * - TCP socket connection to gateway
 * - Asynchronous message reading via reader thread
 * - Socket lifecycle with responsive shutdown via interrupt
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectTcpConnector extends ComfoConnectConnector {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectTcpConnector.class);

    private final String hostname;
    private final int port;
    private final UUID clientUuid;
    private final UUID gatewayUuid;

    private @Nullable Socket socket;
    private @Nullable DataInputStream inputStream;
    private @Nullable DataOutputStream outputStream;
    private final ProtobufFramer framer;

    /**
     * Create a new TCP connector.
     *
     * @param hostname the gateway hostname or IP address
     * @param port the gateway TCP port (default 56747)
     * @param clientUuid the client UUID (random, use for identification)
     * @param gatewayUuid the gateway UUID
     */
    public ComfoConnectTcpConnector(final String hostname, final int port, final UUID clientUuid,
            final UUID gatewayUuid) {
        super();
        this.hostname = hostname;
        this.port = port;
        this.clientUuid = clientUuid;
        this.gatewayUuid = gatewayUuid;
        this.framer = new ProtobufFramer(clientUuid, gatewayUuid);
    }

    /**
     * Create a new TCP connector with default queue capacity.
     *
     * @param hostname the gateway hostname or IP address
     * @param port the gateway TCP port (default 56747)
     * @param clientUuid the client UUID (random, use for identification)
     * @param gatewayUuid the gateway UUID
     * @param queueCapacity the message queue capacity
     */
    public ComfoConnectTcpConnector(final String hostname, final int port, final UUID clientUuid,
            final UUID gatewayUuid, final int queueCapacity) {
        super(queueCapacity);
        this.hostname = hostname;
        this.port = port;
        this.clientUuid = clientUuid;
        this.gatewayUuid = gatewayUuid;
        this.framer = new ProtobufFramer(clientUuid, gatewayUuid);
    }

    @Override
    public void connect() throws IOException {
        logger.info("Connecting to ComfoConnect gateway at {}:{}", hostname, port);

        try {
            Socket socket = new Socket();
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(hostname, port), 5000);

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            this.socket = socket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.isConnected = true;

            logger.info("Connected to ComfoConnect gateway");

            startReaderThread(this::readLoop, "ComfoConnect-Reader");
        } catch (IOException e) {
            logger.error("Failed to connect to ComfoConnect gateway: {}", e.getMessage());
            isConnected = false;
            cleanup();
            throw e;
        }
    }

    @Override
    public void disconnect() {
        logger.info("Disconnecting from ComfoConnect gateway");
        isShutdown = true;
        stopReaderThread();
        cleanup();
        isConnected = false;
        logger.info("Disconnected from ComfoConnect gateway");
    }

    @Override
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
                throw e;
            }
        }
    }

    @Override
    public void sendRpdoRequest(final int pdid, final int type) throws IOException {
        logger.debug("sendRpdoRequest called: pdid={}, type={}", pdid, type);

        try {
            Zehnder.CnRpdoRequest.Builder rpdoBuilder = Zehnder.CnRpdoRequest.newBuilder();
            rpdoBuilder.setPdid(pdid);
            rpdoBuilder.setType(type);
            logger.debug("Built CnRpdoRequest: pdid={}, type={}", pdid, type);

            byte[] frame = getFramer().createFrame(
                    Zehnder.GatewayOperation.newBuilder()
                            .setType(Zehnder.GatewayOperation.OperationType.CnRpdoRequestType).build(),
                    rpdoBuilder.build());

            logger.debug("Created RPDO request frame, length={} bytes", frame.length);
            sendMessage(frame);
            Sensors.findById(pdid).ifPresentOrElse(sensor -> logger.info("RPDO request sent for sensor {}", sensor),
                    () -> logger.info("RPDO request sent for sensor ??? ({})", pdid));
        } catch (IOException e) {
            logger.error("Failed to send RPDO request: {}", e.getMessage());
            throw e;
        }
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

                    if (!queueMessage(frameData)) {
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

    /**
     * Clean up socket resources.
     */
    private void cleanup() {
        clearQueue();

        DataInputStream in = this.inputStream;
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                logger.debug("Error closing input stream: {}", e.getMessage());
            }
        }

        DataOutputStream out = this.outputStream;
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                logger.debug("Error closing output stream: {}", e.getMessage());
            }
        }

        Socket sock = this.socket;
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                logger.debug("Error closing socket: {}", e.getMessage());
            }
        }

        this.socket = null;
        this.inputStream = null;
        this.outputStream = null;
    }

    public ProtobufFramer getFramer() {
        return framer;
    }

    public UUID getClientUuid() {
        return clientUuid;
    }

    public UUID getGatewayUuid() {
        return gatewayUuid;
    }
}
