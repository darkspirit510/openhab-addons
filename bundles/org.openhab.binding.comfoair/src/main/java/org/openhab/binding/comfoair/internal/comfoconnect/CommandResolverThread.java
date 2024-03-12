package org.openhab.binding.comfoair.internal.comfoconnect;

import com.zehnder.proto.Zehnder;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.zehnder.proto.Zehnder.GatewayOperation.OperationType.CnRpdoNotificationType;
import static org.openhab.binding.comfoair.internal.comfoconnect.Sensors.knownSensors;

class CommandResolverThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(CommandResolverThread.class);

    private boolean keepRunning = true;

    Bridge bridge;

    public CommandResolverThread(Bridge bridge) {
        this.bridge = bridge;
    }

    public void run() {
        try {
            while (keepRunning) {
                DataInputStream dIn = new DataInputStream(bridge.socket.getInputStream());

                int length = dIn.readInt();

                byte[] response = new byte[length];

                if (length > 0) {
                    dIn.readFully(response, 0, response.length);
                }

                ComfoConnectCommand result = ComfoConnectCommand.decode(response);

                if (result.command.getType() == CnRpdoNotificationType) {
                    Zehnder.CnRpdoNotification message = (Zehnder.CnRpdoNotification) result.message;

                    Optional<Sensor> maybeSensor = knownSensors.stream()
                        .filter(s -> s.id == message.getPdid())
                        .findFirst();

                    if (maybeSensor.isPresent()) {
                        Sensor sensor = maybeSensor.get();

                        try {
                            bridge.handler.updateState(sensor.channel.getUID(), sensor.valueAsState(message));
                        } catch (Exception e) {
                            byte[] content = message.getData().toByteArray();

                            logger.error("", e);
                            logger.info(sensor.name + " (#" + sensor.id + ") failed to read data " + Arrays.toString(content));
                        }
                    } else {
                        logger.info("Unknown sensor result for id " + message.getPdid());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("", e);
        }
    }

    public void dispose() {
        keepRunning = false;
    }
}
