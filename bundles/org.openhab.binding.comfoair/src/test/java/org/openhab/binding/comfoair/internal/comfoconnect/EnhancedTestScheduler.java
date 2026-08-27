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

import java.util.concurrent.*;

/**
 * Enhanced test scheduler that tracks scheduled tasks for testing.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class EnhancedTestScheduler extends TestScheduler {

    private Runnable lastRunnable;
    private long lastDelay;
    private TimeUnit lastUnit;

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        lastRunnable = command;
        lastDelay = delay;
        lastUnit = unit;
        return super.schedule(command, delay, unit);
    }

    public Runnable getLastRunnable() {
        return lastRunnable;
    }

    public long getLastDelay() {
        return lastDelay;
    }

    public TimeUnit getLastUnit() {
        return lastUnit;
    }

    public void resetTracking() {
        lastRunnable = null;
        lastDelay = 0;
        lastUnit = null;
    }
}
