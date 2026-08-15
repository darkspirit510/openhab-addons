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

import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_BOOL;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_INT16;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_INT64;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_INT8;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT16;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT32;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT8;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of all known sensors for ComfoConnect LAN (Q-Series) devices.
 * Maps sensor/PDO IDs to their metadata, data types, and channel mappings.
 * Each sensor knows how to convert its raw data to openHAB State objects.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class Sensors {

    private static final Logger logger = LoggerFactory.getLogger(Sensors.class);

    // Operating mode value mappings based on PROTOCOL-PDO.md
    private static final Map<Integer, String> OPERATING_MODE_MAP = Map.of(-1, "auto", 1, "limited_manual", 5,
            "unlimited_manual", 6, "boost", 11, "away");

    private static final Map<Integer, String> MANUAL_MODE_MAP = Map.of(-1, "auto", 1, "unlimited_manual");

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
     * Each sensor is configured with its ID, type, channel ID, and any necessary
     * value transformations (e.g., divide by 10 for temperature sensors).
     *
     * @return list of all known sensors
     */
    private static List<Sensor> buildSensorList() {
        List<Sensor> sensors = new ArrayList<>();

        // Device state sensors
        sensors.add(new DecimalSensor("Device State", 16, TYPE_CN_UINT8, "deviceState"));
        sensors.add(new DecimalSensor("Changing filters", 18, TYPE_CN_UINT8, "changingFilters"));

        // Operating mode sensors
        sensors.add(new StringSensor("Operating Mode", 49, TYPE_CN_INT8, "operatingMode")
                .withTransformation(value -> OPERATING_MODE_MAP.getOrDefault(value.intValue(), "unknown")));
        sensors.add(new StringSensor("Manual Mode", 56, TYPE_CN_INT8, "manualMode")
                .withTransformation(value -> MANUAL_MODE_MAP.getOrDefault(value.intValue(), "unknown")));

        // Fan speed and mode sensors
        sensors.add(new DecimalSensor("Fan Speed", 65, TYPE_CN_UINT8, "fanSpeedMode"));
        sensors.add(new DecimalSensor("Bypass Activation State", 66, TYPE_CN_UINT8, "bypassActivationState"));
        sensors.add(new DecimalSensor("Temperature Profile Mode", 67, TYPE_CN_UINT8, "profileTemperature"));
        sensors.add(new DecimalSensor("Supply Fan Mode", 70, TYPE_CN_UINT8, "supplyFanMode"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode", 71, TYPE_CN_UINT8, "exhaustFanMode"));

        // Fan next change sensors
        sensors.add(new DecimalSensor("Fan Speed Next Change", 81, TYPE_CN_UINT32, "nextChangeFan"));
        sensors.add(new DecimalSensor("Bypass Next Change", 82, TYPE_CN_UINT32, "nextChangeBypass"));
        sensors.add(new DecimalSensor("Supply Fan Next Change", 86, TYPE_CN_UINT32, "supplyFanNextChange"));
        sensors.add(new DecimalSensor("Exhaust Fan Next Change", 87, TYPE_CN_UINT32, "exhaustFanNextChange"));

        // Fan mode 2 sensors
        sensors.add(new DecimalSensor("Supply Fan Mode", 54, TYPE_CN_UINT8, "supplyFanMode2"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode", 55, TYPE_CN_UINT8, "exhaustFanMode2"));

        // Fan duty and flow sensors
        sensors.add(new DecimalSensor("Exhaust Fan Duty", 117, TYPE_CN_UINT8, "exhaustFanDuty"));
        sensors.add(new DecimalSensor("Supply Fan Duty", 118, TYPE_CN_UINT8, "supplyFanDuty"));
        sensors.add(new DecimalSensor("Exhaust Fan Flow", 119, TYPE_CN_UINT16, "exhaustFanFlow"));
        sensors.add(new DecimalSensor("Supply Fan Flow", 120, TYPE_CN_UINT16, "supplyFanFlow"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed", 121, TYPE_CN_UINT16, "exhaustFanSpeed"));
        sensors.add(new DecimalSensor("Supply Fan Speed", 122, TYPE_CN_UINT16, "supplyFanSpeed"));

        // Power usage sensors
        sensors.add(new DecimalSensor("Power Usage", 128, TYPE_CN_UINT16, "powerUsage"));
        sensors.add(new DecimalSensor("Power Usage (year)", 129, TYPE_CN_UINT16, "powerUsageTotalYear"));
        sensors.add(new DecimalSensor("Power Usage (total)", 130, TYPE_CN_UINT16, "powerUsageTotal"));

        // Preheater power sensors
        sensors.add(new DecimalSensor("Preheater Power Usage (year)", 144, TYPE_CN_UINT16, "preheaterPowerTotalYear"));
        sensors.add(new DecimalSensor("Preheater Power Usage (total)", 145, TYPE_CN_UINT16, "preheaterPowerTotal"));
        sensors.add(new DecimalSensor("Preheater Power Usage", 146, TYPE_CN_UINT16, "preheaterPower"));

        // RF and filter sensors
        sensors.add(new DecimalSensor("RF Pairing Mode", 176, TYPE_CN_UINT8, "rfPairingMode"));
        sensors.add(
                new DecimalSensor("Days remaining to replace the filter", 192, TYPE_CN_UINT16, "daysToReplaceFilter"));

        // Unit and temperature sensors
        sensors.add(new DecimalSensor("Device Temperature Unit", 208, TYPE_CN_UINT8, "unitTemperature"));
        sensors.add(new DecimalSensor("Running Mean Outdoor Temperature (RMOT)", 209, TYPE_CN_INT16, "rmot")
                .withTransformation(v -> v / 10.0));

        // Season sensors
        sensors.add(new BooleanSensor("Heating Season is active", 210, TYPE_CN_BOOL, "seasonHeatingActive"));
        sensors.add(new BooleanSensor("Cooling Season is active", 211, TYPE_CN_BOOL, "seasonCoolingActive"));

        // Target temperature
        sensors.add(new DecimalSensor("Target Temperature", 212, TYPE_CN_INT16, "targetTemperature")
                .withTransformation(v -> v / 10.0));

        // Avoided heating/cooling sensors
        sensors.add(new DecimalSensor("Avoided Heating Power Usage", 213, TYPE_CN_UINT16, "avoidedHeating"));
        sensors.add(new DecimalSensor("Avoided Heating Power Usage (year)", 214, TYPE_CN_UINT16,
                "avoidedHeatingTotalYear"));
        sensors.add(
                new DecimalSensor("Avoided Heating Power Usage (total)", 215, TYPE_CN_UINT16, "avoidedHeatingTotal"));
        sensors.add(new DecimalSensor("Avoided Cooling Power Usage", 216, TYPE_CN_UINT16, "avoidedCooling"));
        sensors.add(new DecimalSensor("Avoided Cooling Power Usage (year)", 217, TYPE_CN_UINT16,
                "avoidedCoolingTotalYear"));
        sensors.add(
                new DecimalSensor("Avoided Cooling Power Usage (total)", 218, TYPE_CN_UINT16, "avoidedCoolingTotal"));

        // Fan speed modulated and bypass
        sensors.add(new DecimalSensor("Fan Speed (modulated)", 226, TYPE_CN_UINT16, "fanSpeedModeModulated"));
        sensors.add(new DecimalSensor("Bypass State", 227, TYPE_CN_UINT8, "bypassState"));
        sensors.add(new DecimalSensor("frostprotection_unbalance", 228, TYPE_CN_UINT8, "frostProtectionUnbalance"));

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

        sensors.add(new BitmaskSensor("Airflow constraints", 230, TYPE_CN_INT64, "airflowConstraints",
                airflowConstraintBits));

        // Temperature sensors
        sensors.add(new DecimalSensor("Supply Air Temperature", 221, TYPE_CN_INT16, "supplyAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Extract Air Temperature", 274, TYPE_CN_INT16, "extractAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Exhaust Air Temperature", 275, TYPE_CN_INT16, "exhaustAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Outdoor Air Temperature", 276, TYPE_CN_INT16, "outdoorAirTemperature")
                .withTransformation(v -> v / 10.0));

        // Unit airflow
        sensors.add(new DecimalSensor("Device Airflow Unit", 224, TYPE_CN_UINT8, "unitAirflow"));

        // Comfort control mode
        sensors.add(new DecimalSensor("Sensor based ventilation mode", 225, TYPE_CN_UINT8, "comfortControlMode"));

        // Humidity sensors
        sensors.add(new DecimalSensor("Extract Air Humidity", 290, TYPE_CN_UINT8, "extractAirHumidity"));
        sensors.add(new DecimalSensor("Exhaust Air Humidity", 291, TYPE_CN_UINT8, "exhaustAirHumidity"));
        sensors.add(new DecimalSensor("Outdoor Air Humidity", 292, TYPE_CN_UINT8, "outdoorAirHumidity"));
        sensors.add(new DecimalSensor("Outdoor Air Humidity (after preheater)", 293, TYPE_CN_UINT8,
                "humidityAfterPreheater"));
        sensors.add(new DecimalSensor("Supply Air Humidity", 294, TYPE_CN_UINT8, "supplyAirHumidity"));

        // Bypass override
        sensors.add(new DecimalSensor("Bypass Override", 338, TYPE_CN_UINT32, "bypassOverride"));

        // Fan mode 3 sensors
        sensors.add(new DecimalSensor("Supply Fan Mode", 342, TYPE_CN_UINT32, "supplyFanMode3"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode", 343, TYPE_CN_UINT32, "exhaustFanMode3"));

        // Analog input sensors
        sensors.add(new DecimalSensor("Analog Input 1", 369, TYPE_CN_UINT8, "analogInput1")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 2", 370, TYPE_CN_UINT8, "analogInput2")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 3", 371, TYPE_CN_UINT8, "analogInput3")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 4", 372, TYPE_CN_UINT8, "analogInput4")
                .withTransformation(v -> v / 10.0));

        // ComfoFond sensors
        sensors.add(new DecimalSensor("ComfoFond Outdoor Air Temperature", 416, TYPE_CN_INT16, "comfoFondTempOutdoor")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("ComfoFond Ground Temperature", 417, TYPE_CN_INT16, "comfoFondTempGround")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("ComfoFond GHE State Percentage", 418, TYPE_CN_UINT8, "comfoFondGheState"));
        sensors.add(new BooleanSensor("ComfoFond GHE Present", 419, TYPE_CN_BOOL, "comfoFondGhePresent"));

        // ComfoCool sensors
        sensors.add(new DecimalSensor("ComfoCool State", 784, TYPE_CN_UINT8, "comfoCoolState"));
        sensors.add(new DecimalSensor("ComfoCool Condensor Temperature", 802, TYPE_CN_INT16, "comfoCoolCondensorTemp")
                .withTransformation(v -> v / 10.0));

        return sensors;
    }
}
