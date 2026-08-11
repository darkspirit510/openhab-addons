package org.openhab.binding.comfoair.internal.comfoconnect.usecase;

import java.io.IOException;

import org.openhab.binding.comfoair.internal.comfoconnect.RequestExecutor;

import com.google.protobuf.MessageLite;

/**
 * Mock implementation of RequestExecutor for testing.
 */
class MockRequestExecutor implements RequestExecutor {
    private byte[] responseToReturn;
    private IOException exceptionToThrow;
    private InterruptedException interruptException;

    public void setResponseToReturn(byte[] response) {
        this.responseToReturn = response;
        this.exceptionToThrow = null;
        this.interruptException = null;
    }

    public void setExceptionToThrow(IOException exception) {
        this.exceptionToThrow = exception;
        this.responseToReturn = null;
        this.interruptException = null;
    }

    public void setInterruptException(InterruptedException exception) {
        this.interruptException = exception;
        this.responseToReturn = null;
        this.exceptionToThrow = null;
    }

    @Override
    public byte[] execute(MessageLite request) throws IOException, InterruptedException {
        if (interruptException != null) {
            throw interruptException;
        }

        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }

        return responseToReturn;
    }
}
