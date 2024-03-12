/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.comfoair.internal.comfoconnect;

import com.google.protobuf.Message;
import com.zehnder.proto.Zehnder;
import org.openhab.binding.comfoair.internal.comfoconnect.sensor.Sensor;
import org.openhab.core.thing.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static com.zehnder.proto.Zehnder.GatewayOperation.OperationType.CnRpdoRequestType;
import static com.zehnder.proto.Zehnder.GatewayOperation.OperationType.KeepAliveType;
import static com.zehnder.proto.Zehnder.GatewayOperation.OperationType.StartSessionRequestType;

public class Bridge {

    private static final Logger logger = LoggerFactory.getLogger(ComfoConnectHandler.class);

    public static final int PORT = 56747;

    // openHAB CmfCnnct
    static final byte[] ownUuid = new byte[]{111, 112, 101, 110, 72, 65, 66, 32, 67, 109, 102, 67, 110, 110, 99,
        116};

    ComfoConnectHandler handler;
    byte[] bridgeUuid;

    Socket socket;

    CommandResolverThread commandResolverThread = null;
    KeepAliveThread keepAliveThread = null;

    private int reference;

    public Bridge(ComfoConnectHandler handler, byte[] bridgeUuid) {
        this.handler = handler;
        this.bridgeUuid = bridgeUuid;
    }

    public static Bridge discover(ComfoConnectHandler handler) {
        try (DatagramSocket ds = new DatagramSocket()) {
            byte[] discoverMessage = {0x0a, 0x00};
            InetAddress host = InetAddress.getByName(handler.config.host);

            DatagramPacket dp = new DatagramPacket(discoverMessage, discoverMessage.length, host, PORT);
            ds.send(dp);
            ds.setSoTimeout(3000);
            DatagramPacket dpReceive = new DatagramPacket(new byte[37], 37);
            ds.receive(dpReceive);

            Zehnder.DiscoveryOperation result = Zehnder.DiscoveryOperation.parseFrom(dpReceive.getData());

            logger.debug("Bridge with UUID " + prettyPrintUuid(result) + " discovered!");

            return new Bridge(handler, result.getSearchGatewayResponse().getUuid().toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String prettyPrintUuid(Zehnder.DiscoveryOperation result) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(result.getSearchGatewayResponse().getUuid().toByteArray());
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong()).toString();
    }

    public void startSession() {
        Zehnder.StartSessionRequest message = Zehnder.StartSessionRequest.newBuilder()
            .setTakeover(true)
            .build();

        Zehnder.GatewayOperation command = Zehnder.GatewayOperation.newBuilder()
            .setType(StartSessionRequestType)
            .setReference(nextReference())
            .build();

        sendToGateway(command, message);
    }

    public synchronized int nextReference() {
        return reference++;
    }

    private void sendToGateway(Zehnder.GatewayOperation command, Message message) {
        try {
            ComfoConnectCommand wrapper = new ComfoConnectCommand(command, message, ownUuid, bridgeUuid);

            byte[] encodedMessage = wrapper.encode();

            socket.getOutputStream().write(encodedMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void connect() {
        try {
            this.socket = new Socket();

            socket.connect(new InetSocketAddress(handler.config.host, PORT), 10000);

            commandResolverThread = new CommandResolverThread(this);
            keepAliveThread = new KeepAliveThread(this);

            startSession();

            registerSensors();

            commandResolverThread.start();
            keepAliveThread.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void dispose() {
        if(commandResolverThread != null) {
            commandResolverThread.dispose();
        }

        if(keepAliveThread != null) {
            keepAliveThread.dispose();
        }
    }

    private void registerSensors() {
        List<Channel> channels = this.handler.getThing().getChannels();

        channels.stream()
            .filter(this::isLinked)
            .map(Sensors::sensorForChannel)
            .forEach(this::registerSensor);
    }

    private boolean isLinked(Channel c) {
        return handler.getCallback() != null
            && handler.getCallback().isChannelLinked(c.getUID());
    }

    private void registerSensor(Sensor sensor) {
        Zehnder.CnRpdoRequest message = Zehnder.CnRpdoRequest.newBuilder()
            .setPdid(sensor.id)
            .setType(sensor.type.value)
            .setZone(1)
            .build();

        Zehnder.GatewayOperation command = Zehnder.GatewayOperation.newBuilder()
            .setType(CnRpdoRequestType)
            .setReference(nextReference())
            .build();

        sendToGateway(command, message);
    }

    public void sendKeepAlive() throws IOException {
        Zehnder.GatewayOperation command = Zehnder.GatewayOperation.newBuilder()
            .setType(KeepAliveType)
            .setReference(nextReference())
            .build();

        sendToGateway(command, null);
    }
}
