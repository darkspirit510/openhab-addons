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

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectConnector;
import org.openhab.binding.comfoair.internal.comfoconnect.ComfoConnectProtocolHandler;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.HexConverter;
import org.openhab.binding.comfoair.internal.comfoconnect.misc.ProtobufFramer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehnder.proto.Zehnder;
import com.zehnder.proto.Zehnder.GatewayOperation.OperationType;

/**
 * Handles message sending, queuing, and consumption for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class MessageQueue implements MessageQueueManager, MessageConsumer {

    private final Logger logger = LoggerFactory.getLogger(MessageQueue.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 100;

    private final ComfoConnectConnector connector;
    private final ProtobufFramer framer;
    private final HexConverter hexConverter;
    private final ComfoConnectProtocolHandler protocolHandler;
    private final ScheduledExecutorService scheduler;

    // Message queue management
    private final BlockingQueue<byte[]> messageQueue;
    private volatile boolean isShutdown = false;

    // Message consumer
    private @Nullable Future<?> messageConsumerTask;

    /**
     * Create a new Messages instance with full functionality.
     *
     * @param connector the underlying TCP connector
     * @param framer the protobuf framer
     * @param hexConverter converter for hex formatting
     * @param protocolHandler the protocol handler for processing incoming messages
     * @param scheduler executor for running the consumer task
     */
    public MessageQueue(final ComfoConnectConnector connector, final ProtobufFramer framer,
            final HexConverter hexConverter, final ComfoConnectProtocolHandler protocolHandler,
            final ScheduledExecutorService scheduler) {
        this.connector = connector;
        this.framer = framer;
        this.hexConverter = hexConverter;
        this.protocolHandler = protocolHandler;
        this.scheduler = scheduler;
        this.messageQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    }

    // ========== Message Sending Methods (existing functionality) ==========

    public void sendMessage(final byte[] message) throws IOException {
        connector.sendMessage(message);
    }

    public void sendRpdoRequest(final int pdid, final int type) throws IOException {
        logger.debug("sendRpdoRequest called: pdid={}, type={}", pdid, type);

        try {
            Zehnder.CnRpdoRequest.Builder rpdoBuilder = Zehnder.CnRpdoRequest.newBuilder();
            rpdoBuilder.setPdid(pdid);
            rpdoBuilder.setType(type);
            rpdoBuilder.setZone(1); // Zone must be set to 1, matching aiocomfoconnect
            logger.debug("Built CnRpdoRequest: pdid={}, type={}, zone={}", pdid, type, 1);

            byte[] frame = framer.createReferencedFrame(OperationType.CnRpdoRequestType, rpdoBuilder.build());

            logger.debug("Created RPDO request frame, length={} bytes", frame.length);
            sendMessage(frame);
            logger.info("RPDO request sent for sensor with PDID {}", pdid);
        } catch (IOException e) {
            logger.error("Failed to send RPDO request: {}", e.getMessage());
            throw e;
        }
    }

    public void sendRpdoUnsubscribe(final int pdid) throws IOException {
        logger.debug("sendRpdoUnsubscribe called: pdid={}", pdid);

        try {
            // To unsubscribe, send a CnRpdoRequest without the type field
            // According to the protocol: "when no type is specified, a previously registered RPDO with given PDID is
            // deleted"
            Zehnder.CnRpdoRequest.Builder rpdoBuilder = Zehnder.CnRpdoRequest.newBuilder();
            rpdoBuilder.setPdid(pdid);
            rpdoBuilder.setZone(1); // Zone must be set to 1, matching aiocomfoconnect
            // Note: We do NOT set the type field - this is what triggers the unsubscribe
            logger.debug("Built CnRpdoRequest for unsubscribe: pdid={}, zone={}", pdid, 1);

            byte[] frame = framer.createReferencedFrame(OperationType.CnRpdoRequestType, rpdoBuilder.build());

            logger.debug("Created RPDO unsubscribe frame, length={} bytes", frame.length);
            sendMessage(frame);
            logger.info("RPDO unsubscribe sent for sensor with PDID {}", pdid);
        } catch (IOException e) {
            logger.error("Failed to send RPDO unsubscribe: {}", e.getMessage());
            throw e;
        }
    }

    public void sendRmiRequest(final int nodeId, final byte[] rmiMessage) throws IOException {
        logger.debug("sendRmiRequest called: nodeId={}, rmiMessage length={}", nodeId, rmiMessage.length);

        try {
            Zehnder.CnRmiRequest.Builder rmiBuilder = Zehnder.CnRmiRequest.newBuilder();
            rmiBuilder.setNodeId(nodeId);
            rmiBuilder.setMessage(com.google.protobuf.ByteString.copyFrom(rmiMessage));
            logger.debug("Built CnRmiRequest: nodeId={}, message={}", nodeId, hexConverter.toHex(rmiMessage));

            byte[] frame = framer.createReferencedFrame(OperationType.CnRmiRequestType, rmiBuilder.build());

            logger.debug("Created RMI request frame, length={} bytes", frame.length);
            sendMessage(frame);
            logger.info("RMI request sent for node {}: {}", nodeId, hexConverter.toHex(rmiMessage));
        } catch (IOException e) {
            logger.error("Failed to send RMI request: {}", e.getMessage());
            throw e;
        }
    }

    // ========== Message Queue Management Methods (from MessageQueueManagerImpl) ==========

    @Override
    public boolean queueMessage(final byte[] message) {
        if (isShutdown) {
            return false;
        }

        return messageQueue.offer(message);
    }

    @Override
    public byte @Nullable [] getNextMessage() {
        try {
            byte[] message = messageQueue.take();
            logger.info("Retrieved message from queue: {} bytes, first byte: 0x{}", message.length,
                    message.length > 0 ? String.format("%02X", message[0]) : "N/A");
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("getNextMessage interrupted");

            return null;
        }
    }

    @Override
    public byte @Nullable [] pollMessage(final long timeoutMs) {
        try {
            return messageQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("pollMessage interrupted");

            return null;
        }
    }

    @Override
    public void clearQueue() {
        messageQueue.clear();
    }

    @Override
    public void setShutdown(final boolean shutdown) {
        this.isShutdown = shutdown;
    }

    // ========== Message Consumer Methods (from MessageConsumerImpl) ==========

    @Override
    public void startMessageConsumer() {
        logger.debug("Starting message consumer loop");

        messageConsumerTask = scheduler.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] message = getNextMessage();

                    if (message != null) {
                        logger.trace("Message consumer: processing {} bytes", message.length);
                        protocolHandler.handleIncomingMessage(message);
                    }
                }
            } catch (Exception e) {
                logger.warn("Unexpected error in message consumer loop: {}", e.getMessage(), e);
            }
            logger.debug("Message consumer loop stopped");
        });
    }

    @Override
    public void stopMessageConsumer() {
        Future<?> task = messageConsumerTask;
        if (task != null) {
            task.cancel(true);
            messageConsumerTask = null;
        }
    }

    // ========== Utility Methods ==========

    public ProtobufFramer getFramer() {
        return framer;
    }

    public BlockingQueue<byte[]> getMessageQueue() {
        return messageQueue;
    }
}
