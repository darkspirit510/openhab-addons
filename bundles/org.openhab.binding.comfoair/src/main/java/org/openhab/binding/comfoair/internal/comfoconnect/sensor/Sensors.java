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

import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.Boolean;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.SignedByte;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.SignedLong;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.SignedShort;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.UnsignedByte;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.UnsignedInt;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.UnsignedShort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.Channel;

/**
 * Registry of all known sensors for ComfoConnect LAN (Q-Series) devices.
 * Maps sensor/PDO IDs to their metadata, data types, and channel mappings.
 * Each sensor knows how to convert its raw data to openHAB State objects.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class Sensors {

    /**
     * Find the sensor definition for a given channel.
     *
     * @param channel the channel to find the sensor for
     * @return the sensor, or empty if no sensor maps to this channel
     */
    public static Optional<Sensor> sensorForChannel(final Channel channel) {
        String id = channel.getUID().getId();

        // First, try direct match
        Optional<Sensor> directMatch = knownSensors.stream().filter(s -> id.equals(s.channelId)).findFirst()
                .map(s -> s.linkChannel(channel));

        if (directMatch.isPresent()) {
            return directMatch;
        }

        // Check if this channel belongs to a BitmaskSensor
        return knownSensors.stream().filter(s -> s instanceof BitmaskSensor).map(s -> (BitmaskSensor) s)
                .filter(bitmaskSensor -> bitmaskSensor.getBitsForChannel(id) != null).findFirst().map(bitmaskSensor -> {
                    bitmaskSensor.linkChannel(channel);
                    return (Sensor) bitmaskSensor;
                });
    }

    /**
     * Find a sensor by its sensor ID.
     *
     * @param sensorId the sensor ID to find
     * @return the sensor, or empty if not found
     */
    public static Optional<Sensor> findById(final int sensorId) {
        return knownSensors.stream().filter(s -> s.id == sensorId).findFirst();
    }

    /**
     * Find a sensor by its channel ID.
     *
     * @param channelId the channel ID to find
     * @return the sensor, or empty if not found
     */
    public static Optional<Sensor> findByChannelId(final String channelId) {
        return knownSensors.stream().filter(s -> channelId.equals(s.channelId)).findFirst();
    }

    public static final List<Sensor> knownSensors = List.of(
            // Device state sensors
            new DecimalSensor(16, UnsignedByte, "deviceState"), new DecimalSensor(18, UnsignedByte, "changingFilters"),

            // Operating mode sensors
            new OperatingModeSensor(49, SignedByte, "operatingMode"),
            new ManualModeSensor(56, SignedByte, "manualMode"),

            // Fan speed and mode sensors
            new DecimalSensor(65, UnsignedByte, "fanSpeedMode"),
            new DecimalSensor(66, UnsignedByte, "bypassActivationState"),
            new DecimalSensor(67, UnsignedByte, "profileTemperature"),
            new DecimalSensor(70, UnsignedByte, "supplyFanMode"), new DecimalSensor(71, UnsignedByte, "exhaustFanMode"),

            // Fan next change sensors
            new DecimalSensor(81, UnsignedInt, "nextChangeFan"), new DecimalSensor(82, UnsignedInt, "nextChangeBypass"),
            new DecimalSensor(86, UnsignedInt, "supplyFanNextChange"),
            new DecimalSensor(87, UnsignedInt, "exhaustFanNextChange"),

            // Fan mode 2 sensors
            new DecimalSensor(54, UnsignedByte, "supplyFanMode2"),
            new DecimalSensor(55, UnsignedByte, "exhaustFanMode2"),

            // Fan duty and flow sensors
            new DecimalSensor(117, UnsignedByte, "exhaustFanDuty"),
            new DecimalSensor(118, UnsignedByte, "supplyFanDuty"),
            new DecimalSensor(119, UnsignedShort, "exhaustFanFlow"),
            new DecimalSensor(120, UnsignedShort, "supplyFanFlow"),
            new DecimalSensor(121, UnsignedShort, "exhaustFanSpeed"),
            new DecimalSensor(122, UnsignedShort, "supplyFanSpeed"),

            // Power usage sensors
            new DecimalSensor(128, UnsignedShort, "powerUsage"),
            new DecimalSensor(129, UnsignedShort, "powerUsageTotalYear"),
            new DecimalSensor(130, UnsignedShort, "powerUsageTotal"),

            // Preheater power sensors
            new DecimalSensor(144, UnsignedShort, "preheaterPowerTotalYear"),
            new DecimalSensor(145, UnsignedShort, "preheaterPowerTotal"),
            new DecimalSensor(146, UnsignedShort, "preheaterPower"),

            // RF and filter sensors
            new DecimalSensor(176, UnsignedByte, "rfPairingMode"),
            new DecimalSensor(192, UnsignedShort, "daysToReplaceFilter"),

            // Unit and temperature sensors
            new DecimalSensor(208, UnsignedByte, "unitTemperature"), new TenthDecimalSensor(209, SignedShort, "rmot"),

            // Season sensors
            new BooleanSensor(210, Boolean, "seasonHeatingActive"),
            new BooleanSensor(211, Boolean, "seasonCoolingActive"),

            // Target temperature
            new TenthDecimalSensor(212, SignedShort, "targetTemperature"),

            // Avoided heating/cooling sensors
            new DecimalSensor(213, UnsignedShort, "avoidedHeating"),
            new DecimalSensor(214, UnsignedShort, "avoidedHeatingTotalYear"),
            new DecimalSensor(215, UnsignedShort, "avoidedHeatingTotal"),
            new DecimalSensor(216, UnsignedShort, "avoidedCooling"),
            new DecimalSensor(217, UnsignedShort, "avoidedCoolingTotalYear"),
            new DecimalSensor(218, UnsignedShort, "avoidedCoolingTotal"),

            // Fan speed modulated and bypass
            new DecimalSensor(226, UnsignedShort, "fanSpeedModeModulated"),
            new DecimalSensor(227, UnsignedByte, "bypassState"),
            new DecimalSensor(228, UnsignedByte, "frostProtectionUnbalance"),

            // Airflow constraints - bitmask sensor with individual boolean channels
            new BitmaskSensor(230, SignedLong, "airflowConstraints", Map.ofEntries(
                    // Multi-bit constraints (OR logic)
                    Map.entry("airflowConstraintResistance", new int[] { 2, 3 }),
                    Map.entry("airflowConstraintNoiseGuard", new int[] { 5, 7 }),
                    Map.entry("airflowConstraintResistanceGuard", new int[] { 6, 8 }),
                    // Single-bit constraints
                    Map.entry("airflowConstraintPreheaterNegative", new int[] { 4 }),
                    Map.entry("airflowConstraintFrostProtection", new int[] { 9 }),
                    Map.entry("airflowConstraintBypass", new int[] { 10 }),
                    Map.entry("airflowConstraintAnalogInput1", new int[] { 12 }),
                    Map.entry("airflowConstraintAnalogInput2", new int[] { 13 }),
                    Map.entry("airflowConstraintAnalogInput3", new int[] { 14 }),
                    Map.entry("airflowConstraintAnalogInput4", new int[] { 15 }),
                    Map.entry("airflowConstraintHood", new int[] { 16 }),
                    Map.entry("airflowConstraintAnalogPreset", new int[] { 18 }),
                    Map.entry("airflowConstraintComfoCool", new int[] { 19 }),
                    Map.entry("airflowConstraintPreheaterPositive", new int[] { 22 }),
                    Map.entry("airflowConstraintRfSensorFlowPreset", new int[] { 23 }),
                    Map.entry("airflowConstraintRfSensorFlowProportional", new int[] { 24 }),
                    Map.entry("airflowConstraintTemperatureComfort", new int[] { 25 }),
                    Map.entry("airflowConstraintHumidityComfort", new int[] { 26 }),
                    Map.entry("airflowConstraintHumidityProtection", new int[] { 27 }),
                    // CO2 zones
                    Map.entry("airflowConstraintCo2Zone1", new int[] { 47 }),
                    Map.entry("airflowConstraintCo2Zone2", new int[] { 48 }),
                    Map.entry("airflowConstraintCo2Zone3", new int[] { 49 }),
                    Map.entry("airflowConstraintCo2Zone4", new int[] { 50 }),
                    Map.entry("airflowConstraintCo2Zone5", new int[] { 51 }),
                    Map.entry("airflowConstraintCo2Zone6", new int[] { 52 }),
                    Map.entry("airflowConstraintCo2Zone7", new int[] { 53 }),
                    Map.entry("airflowConstraintCo2Zone8", new int[] { 54 }))),

            // Temperature sensors
            new TenthDecimalSensor(221, SignedShort, "supplyAirTemperature"),
            new TenthDecimalSensor(274, SignedShort, "extractAirTemperature"),
            new TenthDecimalSensor(275, SignedShort, "exhaustAirTemperature"),
            new TenthDecimalSensor(276, SignedShort, "outdoorAirTemperature"),

            // Unit airflow
            new DecimalSensor(224, UnsignedByte, "unitAirflow"),

            // Comfort control mode
            new DecimalSensor(225, UnsignedByte, "comfortControlMode"),

            // Humidity sensors
            new DecimalSensor(290, UnsignedByte, "extractAirHumidity"),
            new DecimalSensor(291, UnsignedByte, "exhaustAirHumidity"),
            new DecimalSensor(292, UnsignedByte, "outdoorAirHumidity"),
            new DecimalSensor(293, UnsignedByte, "humidityAfterPreheater"),
            new DecimalSensor(294, UnsignedByte, "supplyAirHumidity"),

            // Bypass override
            new DecimalSensor(338, UnsignedInt, "bypassOverride"),

            // Fan mode 3 sensors
            new DecimalSensor(342, UnsignedInt, "supplyFanMode3"),
            new DecimalSensor(343, UnsignedInt, "exhaustFanMode3"),

            // Analog input sensors
            new TenthDecimalSensor(369, UnsignedByte, "analogInput1"),
            new TenthDecimalSensor(370, UnsignedByte, "analogInput2"),
            new TenthDecimalSensor(371, UnsignedByte, "analogInput3"),
            new TenthDecimalSensor(372, UnsignedByte, "analogInput4"),

            // ComfoFond sensors
            new TenthDecimalSensor(416, SignedShort, "comfoFondTempOutdoor"),
            new TenthDecimalSensor(417, SignedShort, "comfoFondTempGround"),
            new DecimalSensor(418, UnsignedByte, "comfoFondGheState"),
            new BooleanSensor(419, Boolean, "comfoFondGhePresent"),

            // ComfoCool sensors
            new DecimalSensor(784, UnsignedByte, "comfoCoolState"),
            new TenthDecimalSensor(802, SignedShort, "comfoCoolCondensorTemp"));
}
