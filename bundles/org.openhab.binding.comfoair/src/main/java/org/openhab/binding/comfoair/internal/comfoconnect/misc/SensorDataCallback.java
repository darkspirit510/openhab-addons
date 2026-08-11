package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;

import com.zehnder.proto.Zehnder;

/**
 * Callback interface for receiving sensor data updates from the gateway.
 */
@NonNullByDefault
public interface SensorDataCallback {
    /**
     * Called when sensor data is received from the gateway.
     *
     * @param sensor the sensor object
     * @param message the protobuf message containing sensor data
     */
    public void onSensorDataReceived(Sensor sensor, Zehnder.CnRpdoNotification message);
}
