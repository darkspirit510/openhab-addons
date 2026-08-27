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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.MessageLite;
import com.zehnder.proto.Zehnder.GatewayOperation;

/**
 * Handles message framing for ComfoConnect TCP protocol.
 *
 * Message structure:
 * - 4-byte big-endian total message length (includes everything after this field)
 * - 16-byte source UUID (as hex bytes)
 * - 16-byte destination UUID (as hex bytes)
 * - 2-byte big-endian command protobuf length
 * - Serialized GatewayOperation protobuf
 * - Serialized request/response message protobuf
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ProtobufFramer {

    private final Logger logger = LoggerFactory.getLogger(ProtobufFramer.class);

    private static final int MIN_FRAME_SIZE = 4 + 16 + 16; // length + src UUID + dst UUID (minimum)
    private static final int TOTAL_LENGTH_SIZE = 4;
    private static final int UUID_SIZE = 16;
    private static final int COMMAND_LENGTH_SIZE = 2;

    private final UUID sourceUuid;
    private final UUID destinationUuid;
    private final UuidConverter uuidConverter = new UuidConverter();
    private final HexConverter hexConverter = new HexConverter();

    /**
     * Create a new message framer.
     *
     * @param sourceUuid the source UUID for messages sent by this client
     * @param destinationUuid the destination UUID (gateway UUID)
     */
    public ProtobufFramer(final UUID sourceUuid, final UUID destinationUuid) {
        this.sourceUuid = sourceUuid;
        this.destinationUuid = destinationUuid;
    }

    /**
     * Create a complete frame with the given command and optional payload.
     *
     * @param command the GatewayOperation protobuf message
     * @param payload optional additional payload message (null if not needed)
     * @return the complete framed message ready to send
     * @throws IOException if serialization fails
     */
    public byte[] createFrame(final MessageLite command, final @org.eclipse.jdt.annotation.Nullable MessageLite payload)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write UUIDs (2x16 bytes)
        byte[] srcUuidBytes = uuidConverter.toBytes(sourceUuid);
        byte[] dstUuidBytes = uuidConverter.toBytes(destinationUuid);

        // Write command protobuf
        byte[] commandBytes = command.toByteArray();

        // Write payload if present
        byte[] payloadBytes = payload != null ? payload.toByteArray() : new byte[0];

        // Calculate total length: src UUID + dst UUID + command length (2) + command + payload
        int totalLength = UUID_SIZE + UUID_SIZE + COMMAND_LENGTH_SIZE + commandBytes.length + payloadBytes.length;

        // Write everything
        dos.writeInt(totalLength); // 4-byte big-endian total length
        dos.write(srcUuidBytes, 0, UUID_SIZE); // 16-byte source UUID
        dos.write(dstUuidBytes, 0, UUID_SIZE); // 16-byte destination UUID
        dos.writeShort(commandBytes.length); // 2-byte command length
        dos.write(commandBytes); // Command protobuf

        if (payloadBytes.length > 0) {
            dos.write(payloadBytes); // Payload protobuf
        }

        dos.flush();
        byte[] frameBytes = baos.toByteArray();

        // Log hex dump of frame for debugging
        logger.debug("Frame created: {} bytes, hex: {}", frameBytes.length, hexConverter.toHex(frameBytes));

        return frameBytes;
    }

    /**
     * Create a complete frame with the given operation type, reference, and payload.
     * Builds the GatewayOperation internally.
     *
     * @param type the operation type
     * @param reference the reference number
     * @param payload the payload message
     * @return the complete framed message ready to send
     * @throws IOException if serialization fails
     */
    public byte[] createReferencedFrame(final GatewayOperation.OperationType type, final int reference,
            final MessageLite payload) throws IOException {
        GatewayOperation command = GatewayOperation.newBuilder().setType(type).setReference(reference).build();
        return createFrame(command, payload);
    }

    /**
     * Create a complete frame with the given operation type and payload.
     * Builds the GatewayOperation internally with reference=0.
     *
     * @param type the operation type
     * @param payload the payload message
     * @return the complete framed message ready to send
     * @throws IOException if serialization fails
     */
    public byte[] createReferencedFrame(final GatewayOperation.OperationType type, final MessageLite payload)
            throws IOException {
        return createReferencedFrame(type, 0, payload);
    }

    /**
     * Parse a complete message frame from raw bytes.
     * Validates the frame structure and extracts components.
     *
     * @param data the raw bytes (should start with length field)
     * @return a ParsedFrame object containing extracted components, or null if invalid
     */
    public @org.eclipse.jdt.annotation.Nullable ParsedFrame parseFrame(final byte[] data) {
        if (data.length < MIN_FRAME_SIZE) {
            logger.trace("Frame too small: {} bytes", data.length);
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        try {
            // Read total length
            int totalLength = buffer.getInt();

            if (data.length < TOTAL_LENGTH_SIZE + totalLength) {
                logger.trace("Incomplete frame: expected {}, got {}", TOTAL_LENGTH_SIZE + totalLength, data.length);
                return null;
            }

            // Read UUIDs
            byte[] srcUuidBytes = new byte[UUID_SIZE];
            byte[] dstUuidBytes = new byte[UUID_SIZE];
            buffer.get(srcUuidBytes);
            buffer.get(dstUuidBytes);

            UUID srcUuid = uuidConverter.fromBytes(srcUuidBytes);
            UUID dstUuid = uuidConverter.fromBytes(dstUuidBytes);

            // Read command length
            short commandLength = buffer.getShort();

            if (commandLength < 0 || commandLength > totalLength - UUID_SIZE - UUID_SIZE - COMMAND_LENGTH_SIZE) {
                logger.warn("Invalid command length: {}", commandLength);
                return null;
            }

            // Read command
            byte[] commandBytes = new byte[commandLength];
            buffer.get(commandBytes);

            // Remaining bytes are payload
            byte[] payloadBytes = new byte[buffer.remaining()];
            buffer.get(payloadBytes);

            return new ParsedFrame(srcUuid, dstUuid, commandBytes, payloadBytes);

        } catch (Exception e) {
            logger.warn("Error parsing frame: {}", e.getMessage());
            return null;
        }
    }
}
