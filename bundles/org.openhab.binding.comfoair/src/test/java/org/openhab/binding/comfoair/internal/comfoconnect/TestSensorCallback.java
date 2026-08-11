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

import org.openhab.binding.comfoair.internal.comfoconnect.misc.SensorDataCallback;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;

/**
 * Test callback implementation for capturing sensor data in tests.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class TestSensorCallback implements SensorDataCallback {

    private Sensor sensor;
    private int value;
    private boolean called = false;

    @Override
    public void onSensorDataReceived(Sensor sensor, int value) {
        this.sensor = sensor;
        this.value = value;
        this.called = true;
    }

    public boolean wasCalled() {
        return called;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public int getValue() {
        return value;
    }
}
