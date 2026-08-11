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
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT16;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT32;
import static org.openhab.binding.comfoair.internal.comfoconnect.sensor.SensorValueType.TYPE_CN_UINT8;

import java.util.ArrayList;
import java.util.List;
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
        return knownSensors.stream().filter(s -> id.equals(s.channelId)).findFirst().map(s -> s.linkChannel(channel));
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

        // Phase 1: Fan-related sensors (no value corrections)
        sensors.add(new DecimalSensor("Operating Mode", 1, TYPE_CN_UINT8, "operatingMode"));
        sensors.add(new DecimalSensor("Fan Speed", 65, TYPE_CN_UINT8, "ventilationSpeed"));
        sensors.add(new DecimalSensor("Supply Fan Speed Percentage", 66, TYPE_CN_UINT8, "supplyFanSpeedPercentage"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed Percentage", 67, TYPE_CN_UINT8, "exhaustFanSpeedPercentage"));
        sensors.add(
                new DecimalSensor("Supply Fan Speed Percentage Set", 68, TYPE_CN_UINT8, "supplyFanSpeedPercentageSet"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed Percentage Set", 69, TYPE_CN_UINT8,
                "exhaustFanSpeedPercentageSet"));
        sensors.add(new DecimalSensor("Supply Fan Speed (RPM)", 74, TYPE_CN_UINT16, "supplyFanSpeed"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed (RPM)", 75, TYPE_CN_UINT16, "exhaustFanSpeed"));
        sensors.add(new DecimalSensor("Supply Fan Speed Set", 76, TYPE_CN_UINT8, "supplyFanSpeedSet"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed Set", 77, TYPE_CN_UINT8, "exhaustFanSpeedSet"));
        sensors.add(new DecimalSensor("Bypass State", 81, TYPE_CN_UINT8, "bypassState"));
        sensors.add(new DecimalSensor("Preheater State", 82, TYPE_CN_UINT8, "preheaterState"));
        sensors.add(new DecimalSensor("Current Humidity", 10, TYPE_CN_UINT8, "currentHumidity"));
        sensors.add(new DecimalSensor("Target Humidity", 11, TYPE_CN_UINT8, "targetHumidity"));
        sensors.add(new DecimalSensor("Humidifier Humidity", 209, TYPE_CN_UINT8, "humidifierHumidity"));

        // Phase 2: Other basic sensors (no value corrections)
        sensors.add(new DecimalSensor("Week Profile Active", 12, TYPE_CN_UINT8, "weekProfileActive"));
        sensors.add(new DecimalSensor("Global Allergen Mode", 32, TYPE_CN_UINT8, "globalAllergenMode"));
        sensors.add(new DecimalSensor("EWT Speed", 88, TYPE_CN_UINT8, "ewtSpeed"));
        sensors.add(new DecimalSensor("EWT Position", 89, TYPE_CN_UINT8, "ewtPosition"));
        sensors.add(new DecimalSensor("Enthalpy State", 96, TYPE_CN_UINT8, "enthalpyState"));
        sensors.add(new DecimalSensor("Frost Protection Speed", 97, TYPE_CN_UINT8, "frostProtectionSpeed"));
        sensors.add(new DecimalSensor("Frost Protection Loss", 98, TYPE_CN_UINT8, "frostProtectionLoss"));
        sensors.add(new DecimalSensor("Frost Protection Timeout", 99, TYPE_CN_UINT8, "frostProtectionTimeout"));
        sensors.add(new BooleanSensor("HCE Present", 200, TYPE_CN_BOOL, "hcePresent"));

        // Phase 3: Temperature sensors (divide by 10)
        sensors.add(new DecimalSensor("Outdoor Temperature In", 2, TYPE_CN_INT16, "outdoorTemperatureIn")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Outdoor Temperature Out", 3, TYPE_CN_INT16, "outdoorTemperatureOut")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Indoor Temperature In", 4, TYPE_CN_INT16, "indoorTemperatureIn")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Indoor Temperature Out", 5, TYPE_CN_INT16, "indoorTemperatureOut")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("EWT Temperature", 100, TYPE_CN_INT16, "ewtTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Cooker Temperature", 101, TYPE_CN_INT16, "cookerTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Heater Temperature", 102, TYPE_CN_INT16, "heaterTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Pre-Heater Temperature", 103, TYPE_CN_INT16, "preHeaterTemperature")
                .withTransformation(v -> v / 10.0));

        // Phase 3: Humidity sensors (no correction)
        sensors.add(new DecimalSensor("Indoor Humidity", 104, TYPE_CN_UINT8, "indoorHumidity"));
        sensors.add(new DecimalSensor("Exhaust Humidity", 13, TYPE_CN_UINT8, "exhaustHumidity"));
        sensors.add(new DecimalSensor("Indoor Humidity 2", 14, TYPE_CN_UINT8, "indoorHumidity2"));
        sensors.add(new DecimalSensor("Exhaust Humidity 2", 15, TYPE_CN_UINT8, "exhaustHumidity2"));
        sensors.add(new DecimalSensor("Indoor Humidity 3", 16, TYPE_CN_UINT8, "indoorHumidity3"));
        sensors.add(new DecimalSensor("ComfoSupply Humidity", 105, TYPE_CN_UINT8, "comfoSupplyHumidity"));

        // Phase 3: Boolean sensors
        sensors.add(new BooleanSensor("T1 Sensor Present", 17, TYPE_CN_BOOL, "t1SensorPresent"));
        sensors.add(new BooleanSensor("T2 Sensor Present", 18, TYPE_CN_BOOL, "t2SensorPresent"));
        sensors.add(new BooleanSensor("T3 Sensor Present", 21, TYPE_CN_BOOL, "t3SensorPresent"));

        // Phase 4: String sensors
        sensors.add(new DecimalSensor("Temperature Unit", 208, TYPE_CN_UINT8, "temperatureUnit"));

        // Phase 5: Complex sensors
        sensors.add(new DecimalSensor("Airflow Constraints", 230, TYPE_CN_INT64, "airflowConstraints"));

        // Phase 6: Device state sensors
        sensors.add(new DecimalSensor("Operating Mode 2", 49, TYPE_CN_UINT8, "operatingMode2"));
        sensors.add(new DecimalSensor("Sensor 33", 33, TYPE_CN_UINT8, "sensor33"));
        sensors.add(new DecimalSensor("Sensor 37", 37, TYPE_CN_UINT8, "sensor37"));
        sensors.add(new DecimalSensor("Sensor 53", 53, TYPE_CN_UINT8, "sensor53"));
        sensors.add(new DecimalSensor("Operating Mode", 56, TYPE_CN_UINT8, "operatingMode3"));
        sensors.add(new DecimalSensor("RF Pairing Mode", 176, TYPE_CN_UINT8, "rfPairingMode"));
        sensors.add(new DecimalSensor("Days to Replace Filter", 192, TYPE_CN_UINT16, "daysToReplaceFilter"));
        sensors.add(new BooleanSensor("Season Heating Active", 210, TYPE_CN_BOOL, "seasonHeatingActive"));
        sensors.add(new BooleanSensor("Season Cooling Active", 211, TYPE_CN_BOOL, "seasonCoolingActive"));
        sensors.add(new DecimalSensor("Target Temperature", 212, TYPE_CN_INT16, "targetTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Device Airflow Unit", 224, TYPE_CN_UINT8, "unitAirflow"));
        sensors.add(new DecimalSensor("ComfortControl Mode", 225, TYPE_CN_UINT8, "comfortControlMode"));
        sensors.add(new DecimalSensor("Frost Protection Unbalance", 228, TYPE_CN_UINT8, "frostProtectionUnbalance"));

        // Phase 7: Fan-related sensors
        sensors.add(new DecimalSensor("Supply Fan Mode 2", 54, TYPE_CN_UINT8, "supplyFanMode2"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode 2", 55, TYPE_CN_UINT8, "exhaustFanMode2"));
        sensors.add(new DecimalSensor("Supply Fan Mode", 70, TYPE_CN_UINT8, "supplyFanMode"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode", 71, TYPE_CN_UINT8, "exhaustFanMode"));
        sensors.add(new DecimalSensor("Sensor 85", 85, TYPE_CN_UINT32, "sensor85"));
        sensors.add(new DecimalSensor("Supply Fan Next Change", 86, TYPE_CN_UINT32, "supplyFanNextChange"));
        sensors.add(new DecimalSensor("Exhaust Fan Next Change", 87, TYPE_CN_UINT32, "exhaustFanNextChange"));
        sensors.add(new DecimalSensor("Exhaust Fan Duty", 117, TYPE_CN_UINT8, "exhaustFanDuty"));
        sensors.add(new DecimalSensor("Supply Fan Duty", 118, TYPE_CN_UINT8, "supplyFanDuty"));
        sensors.add(new DecimalSensor("Exhaust Fan Flow", 119, TYPE_CN_UINT16, "exhaustFanFlow"));
        sensors.add(new DecimalSensor("Supply Fan Flow", 120, TYPE_CN_UINT16, "supplyFanFlow"));
        sensors.add(new DecimalSensor("Exhaust Fan Speed", 121, TYPE_CN_UINT16, "exhaustFanSpeedRpm"));
        sensors.add(new DecimalSensor("Supply Fan Speed", 122, TYPE_CN_UINT16, "supplyFanSpeedRpm"));
        sensors.add(new DecimalSensor("Fan Speed Mode Modulated", 226, TYPE_CN_UINT16, "fanSpeedModeModulated"));
        sensors.add(new DecimalSensor("Supply Fan Mode 3", 342, TYPE_CN_UINT32, "supplyFanMode3"));
        sensors.add(new DecimalSensor("Exhaust Fan Mode 3", 343, TYPE_CN_UINT32, "exhaustFanMode3"));

        // Phase 8: Bypass sensors
        sensors.add(new DecimalSensor("Bypass State", 227, TYPE_CN_UINT8, "bypassState2"));
        sensors.add(new DecimalSensor("Bypass Override", 338, TYPE_CN_UINT32, "bypassOverride"));

        // Phase 9: Power and energy sensors
        sensors.add(new DecimalSensor("Power Usage", 128, TYPE_CN_UINT16, "powerUsage"));
        sensors.add(new DecimalSensor("Power Usage Total Year", 129, TYPE_CN_UINT16, "powerUsageTotalYear"));
        sensors.add(new DecimalSensor("Power Usage Total", 130, TYPE_CN_UINT16, "powerUsageTotal"));
        sensors.add(new DecimalSensor("Preheater Power Total Year", 144, TYPE_CN_UINT16, "preheaterPowerTotalYear"));
        sensors.add(new DecimalSensor("Preheater Power Total", 145, TYPE_CN_UINT16, "preheaterPowerTotal"));
        sensors.add(new DecimalSensor("Preheater Power", 146, TYPE_CN_UINT16, "preheaterPower"));
        sensors.add(new DecimalSensor("Avoided Heating", 213, TYPE_CN_UINT16, "avoidedHeating"));
        sensors.add(new DecimalSensor("Avoided Heating Total Year", 214, TYPE_CN_UINT16, "avoidedHeatingTotalYear"));
        sensors.add(new DecimalSensor("Avoided Heating Total", 215, TYPE_CN_UINT16, "avoidedHeatingTotal"));
        sensors.add(new DecimalSensor("Avoided Cooling", 216, TYPE_CN_UINT16, "avoidedCooling"));
        sensors.add(new DecimalSensor("Avoided Cooling Total Year", 217, TYPE_CN_UINT16, "avoidedCoolingTotalYear"));
        sensors.add(new DecimalSensor("Avoided Cooling Total", 218, TYPE_CN_UINT16, "avoidedCoolingTotal"));

        // Phase 10: Temperature sensors (divide by 10)
        sensors.add(new DecimalSensor("Sensor 220", 220, TYPE_CN_INT16, "sensor220").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Supply Air Temperature", 221, TYPE_CN_INT16, "supplyAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Extract Air Temperature", 274, TYPE_CN_INT16, "extractAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Exhaust Air Temperature", 275, TYPE_CN_INT16, "exhaustAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Outdoor Air Temperature", 276, TYPE_CN_INT16, "outdoorAirTemperature")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Sensor 277", 277, TYPE_CN_INT16, "sensor277").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Sensor 278", 278, TYPE_CN_INT16, "sensor278").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("ComfoFond Outdoor Temperature", 416, TYPE_CN_INT16, "comfoFondTempOutdoor")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("ComfoFond Ground Temperature", 417, TYPE_CN_INT16, "comfoFondTempGround")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("ComfoCool Condensor Temperature", 802, TYPE_CN_INT16, "comfoCoolCondensorTemp")
                .withTransformation(v -> v / 10.0));

        // Phase 11: Humidity sensors
        sensors.add(new DecimalSensor("Extract Air Humidity", 290, TYPE_CN_UINT8, "extractAirHumidity"));
        sensors.add(new DecimalSensor("Exhaust Air Humidity", 291, TYPE_CN_UINT8, "exhaustAirHumidity"));
        sensors.add(new DecimalSensor("Outdoor Air Humidity", 292, TYPE_CN_UINT8, "outdoorAirHumidity"));
        sensors.add(new DecimalSensor("Humidity After Preheater", 293, TYPE_CN_UINT8, "humidityAfterPreheater"));
        sensors.add(new DecimalSensor("Supply Air Humidity", 294, TYPE_CN_UINT8, "supplyAirHumidity"));

        // Phase 12: Boolean sensors
        sensors.add(new BooleanSensor("ComfoFond GHE Present", 419, TYPE_CN_BOOL, "comfoFondGhePresent"));
        sensors.add(new BooleanSensor("Sensor 785", 785, TYPE_CN_BOOL, "sensor785"));
        sensors.add(new BooleanSensor("Sensor 386", 386, TYPE_CN_BOOL, "sensor386"));
        sensors.add(new BooleanSensor("Sensor 402", 402, TYPE_CN_BOOL, "sensor402"));
        sensors.add(new DecimalSensor("ComfoFond GHE State", 418, TYPE_CN_UINT8, "comfoFondGheState"));

        // Phase 13: Analog input sensors (divide by 10 for voltage)
        sensors.add(new DecimalSensor("Analog Input 1", 369, TYPE_CN_UINT8, "analogInput1")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 2", 370, TYPE_CN_UINT8, "analogInput2")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 3", 371, TYPE_CN_UINT8, "analogInput3")
                .withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Analog Input 4", 372, TYPE_CN_UINT8, "analogInput4")
                .withTransformation(v -> v / 10.0));

        // Phase 14: ComfoCool sensors
        sensors.add(new DecimalSensor("ComfoCool State", 784, TYPE_CN_UINT8, "comfoCoolState"));

        // Phase 15: Miscellaneous sensors
        sensors.add(new DecimalSensor("Sensor 219", 219, TYPE_CN_UINT16, "sensor219"));
        sensors.add(new DecimalSensor("Sensor 321", 321, TYPE_CN_UINT16, "sensor321"));
        sensors.add(new DecimalSensor("Sensor 325", 325, TYPE_CN_UINT16, "sensor325"));
        sensors.add(new DecimalSensor("Sensor 337", 337, TYPE_CN_UINT32, "sensor337"));
        sensors.add(new DecimalSensor("Sensor 341", 341, TYPE_CN_UINT32, "sensor341"));
        sensors.add(new DecimalSensor("Sensor 384", 384, TYPE_CN_INT16, "sensor384").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Sensor 400", 400, TYPE_CN_INT16, "sensor400").withTransformation(v -> v / 10.0));
        sensors.add(new DecimalSensor("Sensor 401", 401, TYPE_CN_UINT8, "sensor401"));

        return sensors;
    }
}
