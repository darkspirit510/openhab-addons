package org.openhab.binding.comfoair.internal.comfoconnect;

import org.openhab.binding.comfoair.internal.comfoconnect.sensor.BooleanSensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.DecimalSensor;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.core.thing.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_BOOL;
import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_INT16;
import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_INT64;
import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_UINT16;
import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_UINT32;
import static org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType.TYPE_CN_UINT8;

public class Sensors {

    public static Sensor sensorForChannel(Channel c) {
        String id = c.getUID().getId();

        Optional<Sensor> sensor = knownSensors
            .stream()
            .filter(s -> id.equals(s.channelId))
            .findFirst();

        if (sensor.isPresent()) {
            return sensor.get().linkChannel(c);
        } else {
            throw new RuntimeException("No Sensor with id" + id);
        }
    }

    public static List<Sensor> knownSensors = asdf();

    private static List<Sensor> asdf() {
        List<Sensor> asdfList = new ArrayList<>();

        asdfList.add(new DecimalSensor("Fan Speed", 65, TYPE_CN_UINT8, "ventilation#fanLevel"));
        asdfList.add(new DecimalSensor("Supply Fan Duty", 118, TYPE_CN_UINT8, "ventilation#fanInPercent"));
        asdfList.add(new DecimalSensor("Exhaust Fan Duty", 117, TYPE_CN_UINT8, "ventilation#fanOutPercent"));
        asdfList.add(new DecimalSensor("Supply Fan Speed", 122, TYPE_CN_UINT16, "ventilation#fanInRPM"));
        asdfList.add(new DecimalSensor("Exhaust Fan Speed", 121, TYPE_CN_UINT16, "ventilation#fanOutRPM"));

        asdfList.add(new DecimalSensor("Target Temperature", 212, TYPE_CN_INT16, "temperatures#targetTemperature").withTransformation(v -> (double) v / 10));
        asdfList.add(new DecimalSensor("Outdoor Air Temperature", 276, TYPE_CN_INT16, "temperatures#outdoorTemperatureIn").withTransformation(v -> (double) v / 10));
        asdfList.add(new DecimalSensor("Exhaust Air Temperature", 275, TYPE_CN_INT16, "temperatures#outdoorTemperatureOut").withTransformation(v -> (double) v / 10));
        asdfList.add(new DecimalSensor("Extract Air Temperature", 274, TYPE_CN_INT16, "temperatures#indoorTemperatureIn").withTransformation(v -> (double) v / 10));
        asdfList.add(new DecimalSensor("Supply Air Temperature", 221, TYPE_CN_INT16, "temperatures#indoorTemperatureOut").withTransformation(v -> (double) v / 10));

        asdfList.add(new DecimalSensor("Device State", 16, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Bypass Activation State", 66, TYPE_CN_UINT8, ""));

        asdfList.add(new DecimalSensor("Changing filters", 18, TYPE_CN_UINT8, ""));

        asdfList.add(new DecimalSensor("Operating Mode", 49, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Operating Mode", 56, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Supply Fan Mode", 54, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Exhaust Fan Mode", 55, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Temperature Profile Mode", 67, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Supply Fan Mode", 70, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Exhaust Fan Mode", 71, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Supply Fan Mode", 342, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("Exhaust Fan Mode", 343, TYPE_CN_UINT32, ""));

        asdfList.add(new DecimalSensor("Fan Speed Next Change", 81, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("Bypass Next Change", 82, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("Supply Fan Next Change", 86, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("Exhaust Fan Next Change", 87, TYPE_CN_UINT32, ""));

        asdfList.add(new DecimalSensor("Exhaust Fan Flow", 119, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Supply Fan Flow", 120, TYPE_CN_UINT16, ""));

        asdfList.add(new DecimalSensor("Extract Air Humidity", 290, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Exhaust Air Humidity", 291, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Outdoor Air Humidity", 292, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Outdoor Air Humidity (after preheater)", 293, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Supply Air Humidity", 294, TYPE_CN_UINT8, ""));

        asdfList.add(new DecimalSensor("Power Usage", 128, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Power Usage (year)", 129, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Power Usage (total)", 130, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Preheater Power Usage (year)", 144, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Preheater Power Usage (total)", 145, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Preheater Power Usage", 146, TYPE_CN_UINT16, ""));

        asdfList.add(new DecimalSensor("RF Pairing Mode", 176, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Days remaining to replace the filter", 192, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Device Temperature Unit", 208, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Running Mean Outdoor Temperature (RMOT)", 209, TYPE_CN_INT16, ""));
        asdfList.add(new BooleanSensor("Heating Season is active", 210, TYPE_CN_BOOL, ""));
        asdfList.add(new BooleanSensor("Cooling Season is active", 211, TYPE_CN_BOOL, ""));
        asdfList.add(new DecimalSensor("Avoided Heating Power Usage", 213, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Avoided Heating Power Usage (year)", 214, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Avoided Heating Power Usage (total)", 215, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Avoided Cooling Power Usage", 216, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Avoided Cooling Power Usage (year)", 217, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Avoided Cooling Power Usage (total)", 218, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Device Airflow Unit", 224, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Sensor based ventilation mode", 225, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Fan Speed (modulated)", 226, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Bypass State", 227, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("frostprotection_unbalance", 228, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Airflow constraints", 230, TYPE_CN_INT64, ""));
        asdfList.add(new DecimalSensor("Bypass Override", 338, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("Analog Input 1", 369, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Analog Input 2", 370, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Analog Input 3", 371, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("Analog Input 4", 372, TYPE_CN_UINT8, ""));

        asdfList.add(new DecimalSensor("sensor_33", 33, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("sensor_37", 37, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("sensor_53", 53, TYPE_CN_UINT8, ""));
        asdfList.add(new DecimalSensor("sensor_85", 85, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("sensor_219", 219, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("Outdoor Air Temperature (?)", 220, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("Outdoor Air Temperature (?)", 277, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("Supply Air Temperature (?)", 278, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("sensor_321", 321, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("sensor_325", 325, TYPE_CN_UINT16, ""));
        asdfList.add(new DecimalSensor("sensor_337", 337, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("sensor_341", 341, TYPE_CN_UINT32, ""));
        asdfList.add(new DecimalSensor("sensor_384", 384, TYPE_CN_INT16, ""));
        asdfList.add(new BooleanSensor("sensor_386", 386, TYPE_CN_BOOL, ""));
        asdfList.add(new DecimalSensor("sensor_400", 400, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("sensor_401", 401, TYPE_CN_UINT8, ""));
        asdfList.add(new BooleanSensor("sensor_402", 402, TYPE_CN_BOOL, ""));
        asdfList.add(new DecimalSensor("sensor_416", 416, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("sensor_417", 417, TYPE_CN_INT16, ""));
        asdfList.add(new DecimalSensor("sensor_418", 418, TYPE_CN_UINT8, ""));
        asdfList.add(new BooleanSensor("sensor_419", 419, TYPE_CN_BOOL, ""));
        asdfList.add(new DecimalSensor("sensor_784", 784, TYPE_CN_UINT8, ""));
        asdfList.add(new BooleanSensor("sensor_785", 785, TYPE_CN_BOOL, ""));
        asdfList.add(new DecimalSensor("sensor_802", 802, TYPE_CN_INT16, ""));

        return asdfList;
    }

}
