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
package org.openhab.binding.comfoair.internal.comfoconnect.sensor;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Functional interface for transforming sensor values.
 * Used to apply transformations like division by 10 for temperature values.
 *
 * @author Sascha Knoop - Initial contribution
 */
@NonNullByDefault
@FunctionalInterface
public interface DecimalTransformation {
    /**
     * Transform a numeric value.
     *
     * @param value the raw value
     * @return the transformed value
     */
    double transform(double value);
}
