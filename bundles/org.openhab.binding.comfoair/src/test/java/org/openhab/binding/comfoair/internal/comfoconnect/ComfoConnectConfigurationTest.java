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

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.openhab.binding.comfoair.internal.ComfoAirBindingConstants;

/**
 * Test class for ComfoConnect configuration and constants.
 *
 * @author Sascha Knoop - Initial contribution
 */
public class ComfoConnectConfigurationTest {

    private static final String DEFAULT_HOSTNAME = "192.168.1.100";
    private static final int DEFAULT_PORT = 56747;
    private static final String DEFAULT_PIN = "1234";

    // ===== Test 1: Configuration has default values =====
    @Test
    public void testConfigurationDefaults() {
        ComfoConnectConfiguration config = new ComfoConnectConfiguration();

        assertEquals(56747, config.port, "Default port should be 56747");
        assertEquals(30, config.refreshInterval, "Default refresh interval should be 30 seconds");
        assertFalse(config.autoTakeover, "Default autoTakeover should be false");
    }

    // ===== Test 2: Configuration fields can be set =====
    @Test
    public void testConfigurationFieldsSettable() {
        ComfoConnectConfiguration config = new ComfoConnectConfiguration();
        config.hostname = "10.0.0.1";
        config.port = 12345;
        config.pin = "9999";
        config.refreshInterval = 60;
        config.autoTakeover = true;
        config.clientUuid = "550e8400-e29b-41d4-a716-446655440000";

        assertEquals("10.0.0.1", config.hostname);
        assertEquals(12345, config.port);
        assertEquals("9999", config.pin);
        assertEquals(60, config.refreshInterval);
        assertTrue(config.autoTakeover);
        assertEquals("550e8400-e29b-41d4-a716-446655440000", config.clientUuid);
    }

    // ===== Test 3: Default client UUID constant is valid =====
    @Test
    public void testDefaultClientUuidIsValid() {
        String defaultUuid = ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID;
        assertNotNull(defaultUuid);

        // Should be parseable as a valid UUID
        UUID uuid = UUID.fromString(defaultUuid);
        assertNotNull(uuid);

        // Should be the "openHAB" + null bytes UUID as documented
        assertEquals("6f70656e-4841-4200-0000-000000000000", uuid.toString());
    }

    // ===== Test 4: Default client UUID comes from "openHAB" string =====
    @Test
    public void testDefaultClientUuidSourceCorrect() {
        // The UUID is built from "openHAB" + 10 null bytes:
        // 6F 70 65 6E 48 41 42 00 00 00 00 00 00 00 00 00
        // Result: 6f70656e-4841-4200-0000-000000000000
        String expected = "6f70656e-4841-4200-0000-000000000000";
        String actual = ComfoAirBindingConstants.COMFOCONNECT_DEFAULT_CLIENT_UUID;
        assertEquals(expected, actual);
    }

    // ===== Test 5: Auto-takeover flag is configurable =====
    @Test
    public void testAutoTakeoverIsConfigurable() {
        ComfoConnectConfiguration config1 = new ComfoConnectConfiguration();
        assertFalse(config1.autoTakeover, "Default should be false");

        ComfoConnectConfiguration config2 = new ComfoConnectConfiguration();
        config2.autoTakeover = true;
        assertTrue(config2.autoTakeover, "Should be settable to true");
    }

    // ===== Test 6: Client UUID is optional in configuration =====
    @Test
    public void testClientUuidOptional() {
        ComfoConnectConfiguration config = new ComfoConnectConfiguration();

        // Should be null by default
        assertNull(config.clientUuid, "Client UUID should be null by default (optional)");

        // Should be settable
        String testUuid = "550e8400-e29b-41d4-a716-446655440000";
        config.clientUuid = testUuid;
        assertEquals(testUuid, config.clientUuid);
    }

    // ===== Test 7: PIN and hostname are required fields =====
    @Test
    public void testRequiredFieldsDocumented() {
        ComfoConnectConfiguration config = new ComfoConnectConfiguration();

        // Both should be null initially (user must provide)
        assertNull(config.hostname);
        assertNull(config.pin);
    }
}
