package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import com.zehnder.proto.Zehnder;
import org.openhab.binding.comfoair.internal.comfoconnect.SensorValueType;
import org.openhab.core.library.types.DecimalType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DecimalSensor extends Sensor {

    private DecimalTransformation transformation = (i) -> i;

    public DecimalSensor(String name, int id, SensorValueType type, String channelId) {
        super(name, id, type, channelId);
    }

    public DecimalSensor withTransformation(DecimalTransformation transformation) {
        this.transformation = transformation;

        return this;
    }

    @Override
    public DecimalType valueAsState(Zehnder.CnRpdoNotification message) {
        return new DecimalType(transformation.transform(extractValueFrom(message)));
    }

    private long extractValueFrom(Zehnder.CnRpdoNotification message) {
        final ByteBuffer byteBuffer = ByteBuffer
            .wrap(message.getData().toByteArray())
            .order(ByteOrder.LITTLE_ENDIAN);

        return switch (type) {
            case TYPE_CN_UINT8 -> unsigned(byteBuffer.get());
            case TYPE_CN_UINT16 -> unsigned(byteBuffer.getShort());
            case TYPE_CN_UINT32 -> unsigned(byteBuffer.getInt());
            case TYPE_CN_INT8 -> byteBuffer.get();
            case TYPE_CN_INT16 -> byteBuffer.getShort();
            case TYPE_CN_INT64 -> byteBuffer.getInt();
            default -> 0;
        };

    }

    private static int unsigned(byte value) {
        return value & 0x000000FF;
    }

    private static int unsigned(short value) {
        return value & 0x0000FFFF;
    }

    private static long unsigned(int value) {
        return value;
    }
}
