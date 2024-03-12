package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import com.zehnder.proto.Zehnder;
import org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType;
import org.openhab.core.thing.Channel;
import org.openhab.core.types.State;

public abstract class Sensor {

    public String name;

    public int id;

    public SensorValueType type;

    public String channelId;

    public Channel channel;

    public Sensor(String name, int id, SensorValueType type) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.channelId = null;
    }

    public Sensor(String name, int id, SensorValueType type, String channelId) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.channelId = channelId;
    }

    public abstract State valueAsState(Zehnder.CnRpdoNotification message);

    public Sensor linkChannel(Channel c) {
        setChannel(c);

        return this;
    }

    private void setChannel(Channel channel) {
        this.channel = channel;
    }
}
