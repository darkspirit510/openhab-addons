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

import java.util.concurrent.CompletableFuture;

/**
 * Container for pending request information.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class PendingRequest<T> {
    public final int reference;
    public final long createdTime;
    public final CompletableFuture<T> future;
    public final Class<T> responseClass;

    public PendingRequest(final int reference, final CompletableFuture<T> future, final Class<T> responseClass) {
        this.reference = reference;
        this.future = future;
        this.responseClass = responseClass;
        this.createdTime = System.currentTimeMillis();
    }

    public boolean isExpired(final long timeoutMs) {
        return (System.currentTimeMillis() - createdTime) > timeoutMs;
    }
}
