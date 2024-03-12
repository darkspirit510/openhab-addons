package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import com.zehnder.proto.Zehnder;
import org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BooleanSensor extends Sensor {

    public BooleanSensor(String name, int id, SensorValueType type, String channelId) {
        super(name, id, type, channelId);
    }

    @Override
    public State valueAsState(Zehnder.CnRpdoNotification message) {
        final ByteBuffer byteBuffer = ByteBuffer
            .wrap(message.getData().toByteArray())
            .order(ByteOrder.LITTLE_ENDIAN);

        return OnOffType.from(byteBuffer.get() == 1);
    }
}
