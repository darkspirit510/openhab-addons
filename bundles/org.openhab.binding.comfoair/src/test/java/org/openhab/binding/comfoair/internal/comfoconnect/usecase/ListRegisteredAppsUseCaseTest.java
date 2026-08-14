/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.comfoair.internal.comfoconnect.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.zehnder.proto.Zehnder;

/**
 * Unit tests for {@link ListRegisteredAppsUseCase}.
 * Uses pure JUnit 5 with inline anonymous classes for test doubles.
 */
@DisplayName("ListRegisteredAppsUseCase")
public class ListRegisteredAppsUseCaseTest {

    private static final UUID TEST_CLIENT_UUID = UUID.fromString("d4c5e8f7-1a2b-3c4d-5e6f-7a8b9c0d1e2f");

    private MockRequestExecutor mockRequestExecutor;
    private ListRegisteredAppsUseCase useCase;

    @BeforeEach
    public void setUp() {
        mockRequestExecutor = new MockRequestExecutor();
        useCase = new ListRegisteredAppsUseCase(mockRequestExecutor);
    }

    @Test
    @DisplayName("Execute with single registered app")
    public void testExecuteSuccessWithSingleApp() throws Exception {
        // Given: Mock response with one registered app
        byte[] mockResponse = createMockResponse(TEST_CLIENT_UUID);
        mockRequestExecutor.setResponseToReturn(mockResponse);

        // When: Execute the use case
        List<UUID> result = useCase.execute();

        // Then: Verify result
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(TEST_CLIENT_UUID, result.get(0));
    }

    @Test
    @DisplayName("Execute with multiple registered apps")
    public void testExecuteSuccessWithMultipleApps() throws Exception {
        // Given: Mock response with multiple registered apps
        UUID app1 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID app2 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID app3 = UUID.fromString("ffffffff-eeee-dddd-cccc-bbbbbbbbbbbb");

        byte[] mockResponse = createMockResponse(app1, app2, app3);
        mockRequestExecutor.setResponseToReturn(mockResponse);

        // When: Execute the use case
        List<UUID> result = useCase.execute();

        // Then: Verify result
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(app1));
        assertTrue(result.contains(app2));
        assertTrue(result.contains(app3));
    }

    @Test
    @DisplayName("Execute with empty app list")
    public void testExecuteSuccessWithEmptyList() throws Exception {
        // Given: Mock response with no apps
        byte[] mockResponse = createMockResponse();
        mockRequestExecutor.setResponseToReturn(mockResponse);

        // When: Execute the use case
        List<UUID> result = useCase.execute();

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Execute propagates IOException")
    public void testExecuteIOExceptionPropagation() {
        // Given: Request executor throws IOException
        IOException expectedException = new IOException("Connection to gateway failed");
        mockRequestExecutor.setExceptionToThrow(expectedException);

        // When/Then: Verify exception is propagated
        IOException actualException = assertThrows(IOException.class, () -> useCase.execute());
        assertSame(expectedException, actualException);
    }

    @Test
    @DisplayName("Execute propagates InterruptedException")
    public void testExecuteInterruptedExceptionPropagation() {
        // Given: Request executor throws InterruptedException
        InterruptedException expectedException = new InterruptedException("Thread interrupted");
        mockRequestExecutor.setInterruptException(expectedException);

        // When/Then: Verify exception is propagated
        InterruptedException actualException = assertThrows(InterruptedException.class, () -> useCase.execute());

        assertSame(expectedException, actualException);
    }

    @Test
    @DisplayName("Execute handles invalid protobuf data")
    public void testExecuteInvalidProtobufData() throws Exception {
        // Given: Request executor returns invalid protobuf data
        byte[] invalidData = new byte[] { 0x00, 0x01, 0x02, 0x03 }; // Not valid protobuf
        mockRequestExecutor.setResponseToReturn(invalidData);

        // When/Then: Verify IOException is thrown
        IOException exception = assertThrows(IOException.class, () -> useCase.execute());

        assertTrue(exception.getMessage().contains("Failed to parse registered apps response"));
        assertInstanceOf(InvalidProtocolBufferException.class, exception.getCause());
    }

    @Test
    @DisplayName("Execute with real protobuf data")
    public void testExecuteWithRealProtobufData() throws Exception {
        // Given: Real protobuf data created programmatically
        UUID realUuid = UUID.randomUUID();
        Zehnder.ListRegisteredAppsConfirm confirm = Zehnder.ListRegisteredAppsConfirm.newBuilder()
                .addApps(Zehnder.ListRegisteredAppsConfirm.App.newBuilder()
                        .setUuid(ByteString.copyFrom(uuidToBytes(realUuid))).setDevicename("Real Device"))
                .build();

        byte[] realResponse = confirm.toByteArray();
        mockRequestExecutor.setResponseToReturn(realResponse);

        // When: Execute the use case
        List<UUID> result = useCase.execute();

        // Then: Verify result matches real data
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(realUuid, result.get(0));
    }

    /**
     * Helper method to create a mock ListRegisteredAppsConfirm response.
     */
    private byte[] createMockResponse(UUID... uuids) {
        Zehnder.ListRegisteredAppsConfirm.Builder builder = Zehnder.ListRegisteredAppsConfirm.newBuilder();

        for (UUID uuid : uuids) {
            Zehnder.ListRegisteredAppsConfirm.App.Builder appBuilder = Zehnder.ListRegisteredAppsConfirm.App
                    .newBuilder();
            appBuilder.setUuid(ByteString.copyFrom(uuidToBytes(uuid)));
            appBuilder.setDevicename("Test Device");
            builder.addApps(appBuilder);
        }

        return builder.build().toByteArray();
    }

    /**
     * Helper method to convert UUID to bytes.
     */
    private byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());

        return bytes;
    }
}
