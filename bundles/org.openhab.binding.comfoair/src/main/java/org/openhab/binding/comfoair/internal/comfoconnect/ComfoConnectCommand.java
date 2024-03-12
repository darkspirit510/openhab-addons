/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.comfoair.internal.comfoconnect;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.zehnder.proto.Zehnder.ChangePinConfirm;
import com.zehnder.proto.Zehnder.CloseSessionConfirm;
import com.zehnder.proto.Zehnder.CnAlarmNotification;
import com.zehnder.proto.Zehnder.CnFupProgramBeginConfirm;
import com.zehnder.proto.Zehnder.CnFupProgramConfirm;
import com.zehnder.proto.Zehnder.CnFupProgramEndConfirm;
import com.zehnder.proto.Zehnder.CnFupReadConfirm;
import com.zehnder.proto.Zehnder.CnFupReadRegisterConfirm;
import com.zehnder.proto.Zehnder.CnFupResetConfirm;
import com.zehnder.proto.Zehnder.CnRmiAsyncConfirm;
import com.zehnder.proto.Zehnder.CnRmiAsyncResponse;
import com.zehnder.proto.Zehnder.CnRmiResponse;
import com.zehnder.proto.Zehnder.CnRpdoConfirm;
import com.zehnder.proto.Zehnder.CnRpdoNotification;
import com.zehnder.proto.Zehnder.CnTimeConfirm;
import com.zehnder.proto.Zehnder.DebugConfirm;
import com.zehnder.proto.Zehnder.DeregisterAppConfirm;
import com.zehnder.proto.Zehnder.FactoryReset;
import com.zehnder.proto.Zehnder.GatewayNotification;
import com.zehnder.proto.Zehnder.GatewayOperation;
import com.zehnder.proto.Zehnder.GetRemoteAccessIdConfirm;
import com.zehnder.proto.Zehnder.GetSupportIdConfirm;
import com.zehnder.proto.Zehnder.GetWebIdConfirm;
import com.zehnder.proto.Zehnder.KeepAlive;
import com.zehnder.proto.Zehnder.ListRegisteredAppsConfirm;
import com.zehnder.proto.Zehnder.RegisterAppConfirm;
import com.zehnder.proto.Zehnder.SetAddressConfirm;
import com.zehnder.proto.Zehnder.SetDeviceSettingsConfirm;
import com.zehnder.proto.Zehnder.SetPushIdConfirm;
import com.zehnder.proto.Zehnder.SetRemoteAccessIdConfirm;
import com.zehnder.proto.Zehnder.SetSupportIdConfirm;
import com.zehnder.proto.Zehnder.SetWebIdConfirm;
import com.zehnder.proto.Zehnder.StartSessionConfirm;
import com.zehnder.proto.Zehnder.UpgradeConfirm;
import com.zehnder.proto.Zehnder.VersionConfirm;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectBadRequest;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectInternalError;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectNoResources;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectNotAllowed;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectNotExist;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectNotReachable;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectOtherSession;
import org.openhab.binding.comfoair.internal.comfoconnect.exception.ComfoConnectRmiError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ComfoConnectCommand {

    public GatewayOperation command;
    public Message message;
    public byte[] source;
    public byte[] destination;

    public ComfoConnectCommand(GatewayOperation command, Message message, byte[] ownUuid, byte[] bridgeUuid) {
        this.command = command;
        this.message = message;
        this.source = ownUuid;
        this.destination = bridgeUuid;
    }

    public byte[] encode() throws IOException {
        byte[] commandBuffer = this.command.toByteArray();
        byte[] messageBuffer = this.message == null ? new byte[0] : this.message.toByteArray();

        byte[] commandLengthBuffer = shortToBuffer(commandBuffer.length);
        byte[] messageLengthBuffer = intToBuffer(16 + 16 + 2 + commandBuffer.length + messageBuffer.length);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(messageLengthBuffer);
        baos.write(source);
        baos.write(destination);
        baos.write(commandLengthBuffer);
        baos.write(commandBuffer);
        baos.write(messageBuffer);

        return baos.toByteArray();
    }

    private byte[] shortToBuffer(int value) {
        return ByteBuffer.allocate(Short.BYTES).order(ByteOrder.BIG_ENDIAN).putShort((short) value).array();
    }

    private static byte[] intToBuffer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }

    private static Integer bufferToInt(byte[] value) {
        return (int) ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    static ComfoConnectCommand decode(byte[] response) throws InvalidProtocolBufferException {
        byte[] src_buf = Arrays.copyOfRange(response, 0, 16);
        byte[] dst_buf = Arrays.copyOfRange(response, 16, 32);
        int cmd_len = bufferToInt(Arrays.copyOfRange(response, 32, 34));
        byte[] cmd_buf = Arrays.copyOfRange(response, 34, 34 + cmd_len);
        byte[] msg_buf = Arrays.copyOfRange(response, 34 + cmd_len, response.length);

        GatewayOperation.parseFrom(cmd_buf);

        GatewayOperation cmd = GatewayOperation.parseFrom(cmd_buf);

        switch (cmd.getResult()) {
            case OK:
                // nothing to do
                break;
            case BAD_REQUEST:
                throw new ComfoConnectBadRequest();
            case INTERNAL_ERROR:
                throw new ComfoConnectInternalError();
            case NOT_REACHABLE:
                throw new ComfoConnectNotReachable();
            case OTHER_SESSION:
                throw new ComfoConnectOtherSession();
            case NOT_ALLOWED:
                throw new ComfoConnectNotAllowed();
            case NO_RESOURCES:
                throw new ComfoConnectNoResources();
            case NOT_EXIST:
                throw new ComfoConnectNotExist();
            case RMI_ERROR:
                throw new ComfoConnectRmiError();
        }

        Message message = switch (cmd.getType()) {
            case SetAddressConfirmType -> SetAddressConfirm.parseFrom(msg_buf);
            case RegisterAppConfirmType -> RegisterAppConfirm.parseFrom(msg_buf);
            case StartSessionConfirmType -> StartSessionConfirm.parseFrom(msg_buf);
            case CloseSessionConfirmType -> CloseSessionConfirm.parseFrom(msg_buf);
            case ListRegisteredAppsConfirmType -> ListRegisteredAppsConfirm.parseFrom(msg_buf);
            case DeregisterAppConfirmType -> DeregisterAppConfirm.parseFrom(msg_buf);
            case ChangePinConfirmType -> ChangePinConfirm.parseFrom(msg_buf);
            case GetRemoteAccessIdConfirmType -> GetRemoteAccessIdConfirm.parseFrom(msg_buf);
            case SetRemoteAccessIdConfirmType -> SetRemoteAccessIdConfirm.parseFrom(msg_buf);
            case GetSupportIdConfirmType -> GetSupportIdConfirm.parseFrom(msg_buf);
            case SetSupportIdConfirmType -> SetSupportIdConfirm.parseFrom(msg_buf);
            case GetWebIdConfirmType -> GetWebIdConfirm.parseFrom(msg_buf);
            case SetWebIdConfirmType -> SetWebIdConfirm.parseFrom(msg_buf);
            case SetPushIdConfirmType -> SetPushIdConfirm.parseFrom(msg_buf);
            case DebugConfirmType -> DebugConfirm.parseFrom(msg_buf);
            case UpgradeConfirmType -> UpgradeConfirm.parseFrom(msg_buf);
            case SetDeviceSettingsConfirmType -> SetDeviceSettingsConfirm.parseFrom(msg_buf);
            case VersionConfirmType -> VersionConfirm.parseFrom(msg_buf);
            case GatewayNotificationType -> GatewayNotification.parseFrom(msg_buf);
            case KeepAliveType -> KeepAlive.parseFrom(msg_buf);
            case FactoryResetType -> FactoryReset.parseFrom(msg_buf);
            case CnTimeConfirmType -> CnTimeConfirm.parseFrom(msg_buf);
//            case CnNodeNotificationType -> CnNodeNotification.parseFrom(msg_buf);
            case CnRmiResponseType -> CnRmiResponse.parseFrom(msg_buf);
            case CnRmiAsyncConfirmType -> CnRmiAsyncConfirm.parseFrom(msg_buf);
            case CnRmiAsyncResponseType -> CnRmiAsyncResponse.parseFrom(msg_buf);
            case CnRpdoConfirmType -> CnRpdoConfirm.parseFrom(msg_buf);
            // Sensor
            case CnRpdoNotificationType -> CnRpdoNotification.parseFrom(msg_buf);
            case CnAlarmNotificationType -> CnAlarmNotification.parseFrom(msg_buf);
            case CnFupReadRegisterConfirmType -> CnFupReadRegisterConfirm.parseFrom(msg_buf);
            case CnFupProgramBeginConfirmType -> CnFupProgramBeginConfirm.parseFrom(msg_buf);
            case CnFupProgramConfirmType -> CnFupProgramConfirm.parseFrom(msg_buf);
            case CnFupProgramEndConfirmType -> CnFupProgramEndConfirm.parseFrom(msg_buf);
            case CnFupReadConfirmType -> CnFupReadConfirm.parseFrom(msg_buf);
            case CnFupResetConfirmType -> CnFupResetConfirm.parseFrom(msg_buf);
            default -> null;
        };

        return new ComfoConnectCommand(cmd, message, src_buf, dst_buf);
    }
}
