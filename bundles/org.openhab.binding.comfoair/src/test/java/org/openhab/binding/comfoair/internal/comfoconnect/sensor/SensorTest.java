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
package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Sensor} class.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorTest {

    @Test
    @DisplayName("toString returns formatted sensor info")
    public void testToString() {
        Sensor sensor = new DecimalSensor("sensor", 42, SensorValueType.TYPE_CN_UINT8, "");

        assertEquals("sensor (42)", sensor.toString());
    }
}
