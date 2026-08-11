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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Sensors} class.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class SensorsTest {

    @Test
    @DisplayName("finds known sensors")
    public void testFindKnownSensors() {
        Sensors.knownSensors.forEach(expectedSensor -> {
            Optional<Sensor> actualSensor = Sensors.findById(expectedSensor.id);

            assertTrue(actualSensor.isPresent());
            assertEquals(expectedSensor, actualSensor.get());
        });
    }

    @Test
    @DisplayName("returns empty for unknown ID")
    public void testFindByIdUnknownSensor() {
        assertFalse(Sensors.findById(99999).isPresent());
    }
}
