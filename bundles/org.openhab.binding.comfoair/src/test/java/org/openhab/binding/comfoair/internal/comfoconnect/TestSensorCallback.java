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

import org.openhab.binding.comfoair.internal.comfoconnect.component.SensorHandler;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;

import com.zehnder.proto.Zehnder;

/**
 * Test callback implementation for capturing sensor data in tests.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class TestSensorCallback extends SensorHandler {

    private Sensor sensor;
    private Zehnder.CnRpdoNotification message;
    private boolean called = false;

    public TestSensorCallback() {
        super(null, null, null);
    }

    @Override
    public void onSensorDataReceived(Sensor sensor, Zehnder.CnRpdoNotification message) {
        this.sensor = sensor;
        this.message = message;
        this.called = true;
    }

    @Override
    public boolean isSensorSubscribed(Sensor sensor) {
        // For testing, always return true so all sensor data is processed
        return true;
    }

    public boolean wasCalled() {
        return called;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public Zehnder.CnRpdoNotification getMessage() {
        return message;
    }
}
