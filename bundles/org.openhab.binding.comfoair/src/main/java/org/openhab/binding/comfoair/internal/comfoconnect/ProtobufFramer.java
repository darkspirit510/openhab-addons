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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.MessageLite;

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
        byte[] srcUuidBytes = uuidToBytes(sourceUuid);
        byte[] dstUuidBytes = uuidToBytes(destinationUuid);

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
        logger.debug("Frame created: {} bytes, hex: {}", frameBytes.length, bytesToHex(frameBytes));

        return frameBytes;
    }

    /**
     * Create a frame with only command, no payload.
     *
     * @param command the GatewayOperation protobuf message
     * @return the complete framed message ready to send
     * @throws IOException if serialization fails
     */
    public byte[] createFrame(final MessageLite command) throws IOException {
        return createFrame(command, null);
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

            UUID srcUuid = bytesToUuid(srcUuidBytes);
            UUID dstUuid = bytesToUuid(dstUuidBytes);

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

    /**
     * Convert a UUID to 16 bytes in network order.
     *
     * @param uuid the UUID
     * @return 16 bytes representing the UUID
     */
    private byte[] uuidToBytes(final UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        ByteBuffer.wrap(bytes).putLong(msb).putLong(lsb);

        return bytes;
    }

    /**
     * Convert 16 bytes to a UUID.
     *
     * @param bytes 16 bytes representing a UUID
     * @return the UUID
     */
    private UUID bytesToUuid(final byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Convert bytes to hexadecimal string for logging.
     *
     * @param bytes the bytes to convert
     * @return hex string representation
     */
    private String bytesToHex(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Container for parsed frame components.
     */
    public static class ParsedFrame {
        public final UUID sourceUuid;
        public final UUID destinationUuid;
        public final byte[] command;
        public final byte[] payload;

        ParsedFrame(final UUID sourceUuid, final UUID destinationUuid, final byte[] command, final byte[] payload) {
            this.sourceUuid = sourceUuid;
            this.destinationUuid = destinationUuid;
            this.command = command;
            this.payload = payload;
        }
    }
}
