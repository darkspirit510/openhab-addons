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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Interface for managing message queues for ComfoConnect protocol.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public interface MessageQueueManager {

    /**
     * Queue a message for delivery.
     *
     * @param message the message to queue
     * @return true if queued successfully, false if queue is full
     */
    boolean queueMessage(byte[] message);

    /**
     * Get the next message from the queue (blocking).
     *
     * @return the next message, or null if interrupted
     */
    byte @Nullable [] getNextMessage();

    /**
     * Poll for a message from the queue with timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the next message, or null if timeout expires
     */
    byte @Nullable [] pollMessage(long timeoutMs);

    /**
     * Clear all messages from the queue.
     */
    void clearQueue();

    /**
     * Set the shutdown flag to stop blocking operations.
     *
     * @param shutdown true to set shutdown flag
     */
    void setShutdown(boolean shutdown);
}
