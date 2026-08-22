package org.openhab.binding.comfoair.internal.comfoconnect.misc;

import java.util.UUID;

/**
 * Container for parsed frame components.
 */
public record ParsedFrame(UUID sourceUuid, UUID destinationUuid, byte[] command, byte[] payload) {
}
