package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import java.util.concurrent.CompletableFuture;

/**
 * Container for pending request information.
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
