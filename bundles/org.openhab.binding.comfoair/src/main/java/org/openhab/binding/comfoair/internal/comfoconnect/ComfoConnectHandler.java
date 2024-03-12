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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ComfoConnectHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
public class ComfoConnectHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ComfoConnectHandler.class);

    protected final ComfoConnectConfiguration config = getConfigAs(ComfoConnectConfiguration.class);

    @Nullable
    private Bridge bridge = null;

    public ComfoConnectHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        dispose();

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.submit(this::connect);
    }

    private void connect() {
        try {
            bridge = Bridge.discover(this);

            bridge.connect();

            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE);

            dispose();

            logger.error("", e);
        }
    }

    @Override
    public void dispose() {
        if (bridge != null) {
            bridge.dispose();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("channelUID = " + channelUID);
        logger.info("command = " + command);
    }

    @Override
    public void handleRemoval() {
        super.handleRemoval();
    }

    @Override
    protected @Nullable ThingHandlerCallback getCallback() {
        return super.getCallback();
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        super.channelLinked(channelUID);

        Channel channel = this.thing.getChannel(channelUID);

        logger.info("Channel linked!");
        logger.info(channelUID.toString());
        logger.info(channel == null ? "null" : channel.toString());
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        super.channelUnlinked(channelUID);
    }

    @Override
    protected void updateState(ChannelUID channelID, State state) {
        super.updateState(channelID, state);
    }
}
