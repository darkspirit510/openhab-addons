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

import java.util.ArrayList;
import java.util.HashMap;
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

    public static final List<Sensor> knownSensors = buildSensorList();

    /**
     * Build the complete list of known sensors.
     *
     * @return list of all known sensors
     */
    private static List<Sensor> buildSensorList() {
        List<Sensor> sensors = new ArrayList<>();

        // Device state sensors
        sensors.add(new DecimalSensor(16, UnsignedByte, "deviceState"));
        sensors.add(new DecimalSensor(18, UnsignedByte, "changingFilters"));

        // Operating mode sensors
        sensors.add(new OperatingModeSensor(49, SignedByte, "operatingMode"));
        sensors.add(new ManualModeSensor(56, SignedByte, "manualMode"));

        // Fan speed and mode sensors
        sensors.add(new DecimalSensor(65, UnsignedByte, "fanSpeedMode"));
        sensors.add(new DecimalSensor(66, UnsignedByte, "bypassActivationState"));
        sensors.add(new DecimalSensor(67, UnsignedByte, "profileTemperature"));
        sensors.add(new DecimalSensor(70, UnsignedByte, "supplyFanMode"));
        sensors.add(new DecimalSensor(71, UnsignedByte, "exhaustFanMode"));

        // Fan next change sensors
        sensors.add(new DecimalSensor(81, UnsignedInt, "nextChangeFan"));
        sensors.add(new DecimalSensor(82, UnsignedInt, "nextChangeBypass"));
        sensors.add(new DecimalSensor(86, UnsignedInt, "supplyFanNextChange"));
        sensors.add(new DecimalSensor(87, UnsignedInt, "exhaustFanNextChange"));

        // Fan mode 2 sensors
        sensors.add(new DecimalSensor(54, UnsignedByte, "supplyFanMode2"));
        sensors.add(new DecimalSensor(55, UnsignedByte, "exhaustFanMode2"));

        // Fan duty and flow sensors
        sensors.add(new DecimalSensor(117, UnsignedByte, "exhaustFanDuty"));
        sensors.add(new DecimalSensor(118, UnsignedByte, "supplyFanDuty"));
        sensors.add(new DecimalSensor(119, UnsignedShort, "exhaustFanFlow"));
        sensors.add(new DecimalSensor(120, UnsignedShort, "supplyFanFlow"));
        sensors.add(new DecimalSensor(121, UnsignedShort, "exhaustFanSpeed"));
        sensors.add(new DecimalSensor(122, UnsignedShort, "supplyFanSpeed"));

        // Power usage sensors
        sensors.add(new DecimalSensor(128, UnsignedShort, "powerUsage"));
        sensors.add(new DecimalSensor(129, UnsignedShort, "powerUsageTotalYear"));
        sensors.add(new DecimalSensor(130, UnsignedShort, "powerUsageTotal"));

        // Preheater power sensors
        sensors.add(new DecimalSensor(144, UnsignedShort, "preheaterPowerTotalYear"));
        sensors.add(new DecimalSensor(145, UnsignedShort, "preheaterPowerTotal"));
        sensors.add(new DecimalSensor(146, UnsignedShort, "preheaterPower"));

        // RF and filter sensors
        sensors.add(new DecimalSensor(176, UnsignedByte, "rfPairingMode"));
        sensors.add(new DecimalSensor(192, UnsignedShort, "daysToReplaceFilter"));

        // Unit and temperature sensors
        sensors.add(new DecimalSensor(208, UnsignedByte, "unitTemperature"));
        sensors.add(new DecimalSensor(209, SignedShort, "rmot").withTransformation(v -> v / 10.0));

        // Season sensors
        sensors.add(new BooleanSensor(210, Boolean, "seasonHeatingActive"));
        sensors.add(new BooleanSensor(211, Boolean, "seasonCoolingActive"));

        // Target temperature
        sensors.add(new DecimalSensor(212, SignedShort, "targetTemperature").withTransformation(v -> v / 10.0));

        // Avoided heating/cooling sensors
        sensors.add(new DecimalSensor(213, UnsignedShort, "avoidedHeating"));
        sensors.add(new DecimalSensor(214, UnsignedShort, "avoidedHeatingTotalYear"));
        sensors.add(new DecimalSensor(215, UnsignedShort, "avoidedHeatingTotal"));
        sensors.add(new DecimalSensor(216, UnsignedShort, "avoidedCooling"));
        sensors.add(new DecimalSensor(217, UnsignedShort, "avoidedCoolingTotalYear"));
        sensors.add(new DecimalSensor(218, UnsignedShort, "avoidedCoolingTotal"));

        // Fan speed modulated and bypass
        sensors.add(new DecimalSensor(226, UnsignedShort, "fanSpeedModeModulated"));
        sensors.add(new DecimalSensor(227, UnsignedByte, "bypassState"));
        sensors.add(new DecimalSensor(228, UnsignedByte, "frostProtectionUnbalance"));

        // Airflow constraints - bitmask sensor with individual boolean channels
        Map<String, int[]> airflowConstraintBits = new HashMap<>();
        // Multi-bit constraints (OR logic)
        airflowConstraintBits.put("airflowConstraintResistance", new int[] { 2, 3 });
        airflowConstraintBits.put("airflowConstraintNoiseGuard", new int[] { 5, 7 });
        airflowConstraintBits.put("airflowConstraintResistanceGuard", new int[] { 6, 8 });
        // Single-bit constraints
        airflowConstraintBits.put("airflowConstraintPreheaterNegative", new int[] { 4 });
        airflowConstraintBits.put("airflowConstraintFrostProtection", new int[] { 9 });
        airflowConstraintBits.put("airflowConstraintBypass", new int[] { 10 });
        airflowConstraintBits.put("airflowConstraintAnalogInput1", new int[] { 12 });
        airflowConstraintBits.put("airflowConstraintAnalogInput2", new int[] { 13 });
        airflowConstraintBits.put("airflowConstraintAnalogInput3", new int[] { 14 });
        airflowConstraintBits.put("airflowConstraintAnalogInput4", new int[] { 15 });
        airflowConstraintBits.put("airflowConstraintHood", new int[] { 16 });
        airflowConstraintBits.put("airflowConstraintAnalogPreset", new int[] { 18 });
        airflowConstraintBits.put("airflowConstraintComfoCool", new int[] { 19 });
        airflowConstraintBits.put("airflowConstraintPreheaterPositive", new int[] { 22 });
        airflowConstraintBits.put("airflowConstraintRfSensorFlowPreset", new int[] { 23 });
        airflowConstraintBits.put("airflowConstraintRfSensorFlowProportional", new int[] { 24 });
        airflowConstraintBits.put("airflowConstraintTemperatureComfort", new int[] { 25 });
        airflowConstraintBits.put("airflowConstraintHumidityComfort", new int[] { 26 });
        airflowConstraintBits.put("airflowConstraintHumidityProtection", new int[] { 27 });
        // CO2 zones
        airflowConstraintBits.put("airflowConstraintCo2Zone1", new int[] { 47 });
        airflowConstraintBits.put("airflowConstraintCo2Zone2", new int[] { 48 });
        airflowConstraintBits.put("airflowConstraintCo2Zone3", new int[] { 49 });
        airflowConstraintBits.put("airflowConstraintCo2Zone4", new int[] { 50 });
        airflowConstraintBits.put("airflowConstraintCo2Zone5", new int[] { 51 });
        airflowConstraintBits.put("airflowConstraintCo2Zone6", new int[] { 52 });
        airflowConstraintBits.put("airflowConstraintCo2Zone7", new int[] { 53 });
        airflowConstraintBits.put("airflowConstraintCo2Zone8", new int[] { 54 });

        sensors.add(new BitmaskSensor(230, SignedLong, "airflowConstraints", airflowConstraintBits));

        // Temperature sensors
        sensors.add(new DecimalSensor(221, SignedShort, "supplyAirTemperature").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(274, SignedShort, "extractAirTemperature").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(275, SignedShort, "exhaustAirTemperature").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(276, SignedShort, "outdoorAirTemperature").withTransformation(v -> v / 10.0));

        // Unit airflow
        sensors.add(new DecimalSensor(224, UnsignedByte, "unitAirflow"));

        // Comfort control mode
        sensors.add(new DecimalSensor(225, UnsignedByte, "comfortControlMode"));

        // Humidity sensors
        sensors.add(new DecimalSensor(290, UnsignedByte, "extractAirHumidity"));
        sensors.add(new DecimalSensor(291, UnsignedByte, "exhaustAirHumidity"));
        sensors.add(new DecimalSensor(292, UnsignedByte, "outdoorAirHumidity"));
        sensors.add(new DecimalSensor(293, UnsignedByte, "humidityAfterPreheater"));
        sensors.add(new DecimalSensor(294, UnsignedByte, "supplyAirHumidity"));

        // Bypass override
        sensors.add(new DecimalSensor(338, UnsignedInt, "bypassOverride"));

        // Fan mode 3 sensors
        sensors.add(new DecimalSensor(342, UnsignedInt, "supplyFanMode3"));
        sensors.add(new DecimalSensor(343, UnsignedInt, "exhaustFanMode3"));

        // Analog input sensors
        sensors.add(new DecimalSensor(369, UnsignedByte, "analogInput1").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(370, UnsignedByte, "analogInput2").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(371, UnsignedByte, "analogInput3").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(372, UnsignedByte, "analogInput4").withTransformation(v -> v / 10.0));

        // ComfoFond sensors
        sensors.add(new DecimalSensor(416, SignedShort, "comfoFondTempOutdoor").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(417, SignedShort, "comfoFondTempGround").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor(418, UnsignedByte, "comfoFondGheState"));
        sensors.add(new BooleanSensor(419, Boolean, "comfoFondGhePresent"));

        // ComfoCool sensors
        sensors.add(new DecimalSensor(784, UnsignedByte, "comfoCoolState"));
        sensors.add(new DecimalSensor(802, SignedShort, "comfoCoolCondensorTemp").withTransformation(v -> v / 10.0));

        return sensors;
    }
}
