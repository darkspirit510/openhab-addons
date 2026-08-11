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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.types.State;

/**
 * Registry of all known sensors for ComfoConnect LAN (Q-Series) devices.
 * Maps sensor/PDO IDs to their metadata, data types, and channel mappings.
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

    /**
     * Get the state for a sensor given its ID and raw value.
     * This method contains the logic previously in handleSensorData switch-case.
     *
     * @param sensorId the sensor ID
     * @param rawValue the raw value from the sensor
     * @return the state to set on the channel, or null if sensor not found or needs special handling
     */
    public static @Nullable State getStateForSensor(final int sensorId, final int rawValue) {
        switch (sensorId) {
            // Phase 1: Fan-related sensors
            case 1: // SENSOR_OPERATING_MODE
                return new QuantityType<>(rawValue, Units.ONE);
            case 65: // SENSOR_FAN_SPEED_MODE (Fan speed - already in use)
                // Special handling for fan speed - delegate to handler
                return null; // Will be handled separately
            case 66: // SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 67: // SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 68: // SENSOR_SUPPLY_FAN_SPEED_PERCENTAGE_SET
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 69: // SENSOR_EXHAUST_FAN_SPEED_PERCENTAGE_SET
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 74: // SENSOR_SUPPLY_FAN_SPEED
                return new QuantityType<>(rawValue, Units.ONE);
            case 75: // SENSOR_EXHAUST_FAN_SPEED
                return new QuantityType<>(rawValue, Units.ONE);
            case 76: // SENSOR_SUPPLY_FAN_SPEED_SET
                return new QuantityType<>(rawValue, Units.ONE);
            case 77: // SENSOR_EXHAUST_FAN_SPEED_SET
                return new QuantityType<>(rawValue, Units.ONE);
            case 81: // SENSOR_BYPASS_STATE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 82: // SENSOR_PREHEATER_STATE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 10: // SENSOR_CURRENT_HUMIDITY
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 11: // SENSOR_TARGET_HUMIDITY
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 209: // SENSOR_HUMIDIFIER_HUMIDITY
                return new QuantityType<>(rawValue, Units.PERCENT);

            // Phase 2: Other basic sensors
            case 12: // SENSOR_WEEK_PROFILE_ACTIVE
                return new QuantityType<>(rawValue, Units.ONE);
            case 32: // SENSOR_GLOBAL_ALLERGEN_MODE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 88: // SENSOR_EWT_SPEED
                return new QuantityType<>(rawValue, Units.HERTZ);
            case 89: // SENSOR_EWT_POSITION
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 96: // SENSOR_ENTHALPY_STATE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 97: // SENSOR_FROST_PROTECTION_SPEED
                return new QuantityType<>(rawValue, Units.HERTZ);
            case 98: // SENSOR_FROST_PROTECTION_LOSS
                return new QuantityType<>(rawValue, Units.ONE);
            case 99: // SENSOR_FROST_PROTECTION_TIMEOUT
                return new QuantityType<>(rawValue, Units.ONE);
            case 200: // SENSOR_HCE_PRESENT
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;

            // Phase 3: Sensors with value corrections
            // Temperature sensors (divide by 10)
            case 2: // SENSOR_OUTDOOR_TEMPERATURE_IN
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 3: // SENSOR_OUTDOOR_TEMPERATURE_OUT
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 4: // SENSOR_INDOOR_TEMPERATURE_IN
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 5: // SENSOR_INDOOR_TEMPERATURE_OUT
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 100: // SENSOR_EWT_TEMPERATURE
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 101: // SENSOR_COOKER_TEMPERATURE
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 102: // SENSOR_HEATER_TEMPERATURE
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 103: // SENSOR_PRE_HEATER_TEMPERATURE
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 104: // SENSOR_INDOOR_HUMIDITY (temperature from raw data)
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);

            // Humidity sensors (no correction)
            case 13: // SENSOR_EXHAUST_HUMIDITY
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 14: // SENSOR_INDOOR_HUMIDITY_2
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 15: // SENSOR_EXHAUST_HUMIDITY_2
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 16: // SENSOR_INDOOR_HUMIDITY_3
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 105: // SENSOR_COMFOSUPPLY_HUMIDITY
                return new QuantityType<>(rawValue, Units.PERCENT);

            // Boolean sensors
            case 17: // SENSOR_T1_SENSOR_PRESENT
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 18: // SENSOR_T2_SENSOR_PRESENT
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 21: // SENSOR_T3_SENSOR_PRESENT
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;

            // Mapping sensor (Temperature unit: 0=Celsius, else=Fahrenheit)
            case 208: // SENSOR_TEMPERATURE_UNIT
                return new StringType(rawValue == 0 ? "Celsius" : "Fahrenheit");

            // Phase 4: Complex sensors
            case 230: // SENSOR_AIRFLOW_CONSTRAINTS
                return new StringType(calculateAirflowConstraints(rawValue));

            // Phase 5: Device state sensors
            case 49: // SENSOR_OPERATING_MODE_2
                return new QuantityType<>(rawValue, Units.ONE);
            case 53: // sensor_53
                return new QuantityType<>(rawValue, Units.ONE);
            case 56: // SENSOR_OPERATING_MODE
                return new QuantityType<>(rawValue, Units.ONE);
            case 176: // SENSOR_RF_PAIRING_MODE
                return new QuantityType<>(rawValue, Units.ONE);
            case 192: // SENSOR_DAYS_TO_REPLACE_FILTER
                return new QuantityType<>(rawValue, Units.ONE);
            case 224: // SENSOR_UNIT_AIRFLOW
                return new StringType(rawValue == 3 ? "m3ph" : "lps");
            case 225: // SENSOR_COMFORTCONTROL_MODE
                return new QuantityType<>(rawValue, Units.ONE);
            case 228: // SENSOR_FROSTPROTECTION_UNBALANCE
                return new QuantityType<>(rawValue, Units.ONE);

            // Phase 6: Fan-related sensors
            case 54: // SENSOR_FAN_MODE_SUPPLY_2
                return new QuantityType<>(rawValue, Units.ONE);
            case 55: // SENSOR_FAN_MODE_EXHAUST_2
                return new QuantityType<>(rawValue, Units.ONE);
            case 70: // SENSOR_FAN_MODE_SUPPLY
                return new QuantityType<>(rawValue, Units.ONE);
            case 71: // SENSOR_FAN_MODE_EXHAUST
                return new QuantityType<>(rawValue, Units.ONE);
            case 85: // sensor_85
                return new QuantityType<>(rawValue, Units.ONE);
            case 86: // SENSOR_NEXT_CHANGE_FAN_SUPPLY
                return new QuantityType<>(rawValue, Units.ONE);
            case 87: // SENSOR_NEXT_CHANGE_FAN_EXHAUST
                return new QuantityType<>(rawValue, Units.ONE);
            case 117: // SENSOR_FAN_EXHAUST_DUTY
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 118: // SENSOR_FAN_SUPPLY_DUTY
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 119: // SENSOR_FAN_EXHAUST_FLOW
                return new QuantityType<>(rawValue, SIUnits.CUBIC_METRE.divide(Units.HOUR));
            case 120: // SENSOR_FAN_SUPPLY_FLOW
                return new QuantityType<>(rawValue, SIUnits.CUBIC_METRE.divide(Units.HOUR));
            case 121: // SENSOR_FAN_EXHAUST_SPEED
                return new QuantityType<>(rawValue, Units.HERTZ);
            case 122: // SENSOR_FAN_SUPPLY_SPEED
                return new QuantityType<>(rawValue, Units.HERTZ);
            case 226: // SENSOR_FAN_SPEED_MODE_MODULATED
                return new QuantityType<>(rawValue, Units.ONE);
            case 342: // SENSOR_FAN_MODE_SUPPLY_3
                return new QuantityType<>(rawValue, Units.ONE);
            case 343: // SENSOR_FAN_MODE_EXHAUST_3
                return new QuantityType<>(rawValue, Units.ONE);

            // Phase 7: Bypass sensors
            case 227: // SENSOR_BYPASS_STATE
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 338: // SENSOR_BYPASS_OVERRIDE
                return new QuantityType<>(rawValue, Units.ONE);

            // Phase 8: Power and energy sensors
            case 128: // SENSOR_POWER_USAGE
                return new QuantityType<>(rawValue, Units.WATT);
            case 129: // SENSOR_POWER_USAGE_TOTAL_YEAR
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 130: // SENSOR_POWER_USAGE_TOTAL
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 144: // SENSOR_PREHEATER_POWER_TOTAL_YEAR
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 145: // SENSOR_PREHEATER_POWER_TOTAL
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 146: // SENSOR_PREHEATER_POWER
                return new QuantityType<>(rawValue, Units.WATT);
            case 213: // SENSOR_AVOIDED_HEATING
                return new QuantityType<>(rawValue, Units.WATT);
            case 214: // SENSOR_AVOIDED_HEATING_TOTAL_YEAR
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 215: // SENSOR_AVOIDED_HEATING_TOTAL
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 216: // SENSOR_AVOIDED_COOLING
                return new QuantityType<>(rawValue, Units.WATT);
            case 217: // SENSOR_AVOIDED_COOLING_TOTAL_YEAR
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);
            case 218: // SENSOR_AVOIDED_COOLING_TOTAL
                return new QuantityType<>(rawValue, Units.KILOWATT_HOUR);

            // Phase 9: Temperature sensors (divide by 10)
            case 212: // SENSOR_TARGET_TEMPERATURE
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 220: // sensor_220
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 221: // SENSOR_TEMPERATURE_SUPPLY
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 274: // SENSOR_TEMPERATURE_EXTRACT
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 275: // SENSOR_TEMPERATURE_EXHAUST
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 276: // SENSOR_TEMPERATURE_OUTDOOR
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 277: // sensor_277
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 278: // sensor_278
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 416: // SENSOR_COMFOFOND_TEMP_OUTDOOR
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 417: // SENSOR_COMFOFOND_TEMP_GROUND
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 802: // SENSOR_COMFOCOOL_CONDENSOR_TEMP
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);

            // Phase 10: Humidity sensors
            case 290: // SENSOR_HUMIDITY_EXTRACT
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 291: // SENSOR_HUMIDITY_EXHAUST
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 292: // SENSOR_HUMIDITY_OUTDOOR
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 293: // SENSOR_HUMIDITY_AFTER_PREHEATER
                return new QuantityType<>(rawValue, Units.PERCENT);
            case 294: // SENSOR_HUMIDITY_SUPPLY
                return new QuantityType<>(rawValue, Units.PERCENT);

            // Phase 11: Boolean sensors
            case 210: // SENSOR_SEASON_HEATING_ACTIVE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 211: // SENSOR_SEASON_COOLING_ACTIVE
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 419: // SENSOR_COMFOFOND_GHE_PRESENT
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 785: // sensor_785
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 386: // sensor_386
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 402: // sensor_402
                return rawValue != 0 ? OnOffType.ON : OnOffType.OFF;
            case 418: // SENSOR_COMFOFOND_GHE_STATE
                return new QuantityType<>(rawValue, Units.PERCENT);

            // Phase 12: Analog input sensors (divide by 10 for voltage)
            case 369: // SENSOR_ANALOG_INPUT_1
                return new QuantityType<>(rawValue / 10.0, Units.VOLT);
            case 370: // SENSOR_ANALOG_INPUT_2
                return new QuantityType<>(rawValue / 10.0, Units.VOLT);
            case 371: // SENSOR_ANALOG_INPUT_3
                return new QuantityType<>(rawValue / 10.0, Units.VOLT);
            case 372: // SENSOR_ANALOG_INPUT_4
                return new QuantityType<>(rawValue / 10.0, Units.VOLT);

            // Phase 13: ComfoCool sensors
            case 784: // SENSOR_COMFOCOOL_STATE
                return new QuantityType<>(rawValue, Units.ONE);

            // Phase 14: Miscellaneous sensors
            case 33: // sensor_33
                return new QuantityType<>(rawValue, Units.ONE);
            case 37: // sensor_37
                return new QuantityType<>(rawValue, Units.ONE);
            case 219: // sensor_219
                return new QuantityType<>(rawValue, Units.ONE);
            case 321: // sensor_321
                return new QuantityType<>(rawValue, Units.ONE);
            case 325: // sensor_325
                return new QuantityType<>(rawValue, Units.ONE);
            case 337: // sensor_337
                return new QuantityType<>(rawValue, Units.ONE);
            case 341: // sensor_341
                return new QuantityType<>(rawValue, Units.ONE);
            case 384: // sensor_384
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 400: // sensor_400
                return new QuantityType<>(rawValue / 10.0, SIUnits.CELSIUS);
            case 401: // sensor_401
                return new QuantityType<>(rawValue, Units.ONE);

            default:
                return null; // Unknown sensor
        }
    }

    /**
     * Calculate airflow constraints from the raw sensor value using bit-shifting.
     * Maps bit positions to constraint names.
     *
     * @param rawValue the raw sensor value (64-bit integer as int)
     * @return comma-separated string of active constraints, or empty string if none
     */
    private static String calculateAirflowConstraints(final int rawValue) {
        // Constraint bit position mappings (from Python implementation)
        final String[] constraints = new String[64];
        constraints[2] = "Resistance";
        constraints[4] = "PreheaterNegative";
        constraints[6] = "PreheaterOutdoorTemperature";
        constraints[7] = "PreheaterLimitTa";
        constraints[8] = "PreheaterActivated";
        constraints[9] = "PreheaterErrorNTC";
        constraints[10] = "BypassErrorWet";
        constraints[11] = "BypassErrorFrost";
        constraints[12] = "BypassActivated";
        constraints[13] = "FrostProtectionMinSpeed";
        constraints[14] = "FrostProtectionOutdoor";
        constraints[15] = "FrostProtectionIndoor";
        constraints[16] = "FrostProtectionFailed";
        constraints[19] = "EnthalpyBypassLowIndoor";
        constraints[20] = "EnthalpyBypassWarmOutdoor";
        constraints[21] = "EnthalpyBypassColdOutdoor";
        constraints[22] = "EnthalpyActivated";
        constraints[23] = "CookingZoneActive";
        constraints[24] = "AnalogInput1";
        constraints[25] = "AnalogInput2";
        constraints[26] = "AnalogInput3";
        constraints[27] = "AnalogInput4";

        StringBuilder constraintList = new StringBuilder();
        for (int bit = 0; bit < 32; bit++) { // Check 32 bits since we're using int
            if ((rawValue & (1 << bit)) != 0 && bit < constraints.length && constraints[bit] != null) {
                if (constraintList.length() > 0) {
                    constraintList.append(", ");
                }
                constraintList.append(constraints[bit]);
            }
        }

        return constraintList.toString();
    }

    public static final List<Sensor> knownSensors = buildSensorList();

    /**
     * Build the complete list of known sensors.
     * Maps all sensor IDs to their metadata with transformations where needed.
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
        sensors.add(new DecimalSensor("Operating Mode", 56, TYPE_CN_UINT8, "operatingMode2"));
        sensors.add(new DecimalSensor("RF Pairing Mode", 176, TYPE_CN_UINT8, "rfPairingMode"));
        sensors.add(new DecimalSensor("Days to Replace Filter", 192, TYPE_CN_UINT16, "daysToReplaceFilter"));
        sensors.add(new DecimalSensor("Temperature Unit", 208, TYPE_CN_UINT8, "temperatureUnit"));
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
        sensors.add(new DecimalSensor("Bypass State", 227, TYPE_CN_UINT8, "bypassState"));
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
