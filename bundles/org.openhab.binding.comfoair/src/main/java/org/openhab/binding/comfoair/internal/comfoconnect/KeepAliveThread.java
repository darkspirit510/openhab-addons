package org.openhab.binding.comfoair.internal.comfoconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class KeepAliveThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(KeepAliveThread.class);

    private boolean keepRunning = true;

    Bridge bridge;

    public KeepAliveThread(Bridge bridge) {
        this.bridge = bridge;
    }

    public void run() {
        try {
            while (keepRunning) {
                bridge.sendKeepAlive();

                Thread.sleep(30000L);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("", e);

            bridge.handler.initialize();
        }
    }


    public void dispose() {
        keepRunning = false;
    }
}
