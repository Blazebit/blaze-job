/*
 * Copyright 2018 - 2025 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.job.memory.storage;

import com.blazebit.actor.spi.StateReturningEvent;

/**
 * An event to retrieve a specific amount of sequence values.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class NextSequenceValueEvent implements StateReturningEvent<long[]> {

    private final int amount;
    private long[] sequenceValues;

    /**
     * Constructs an event for requesting a specific amount of sequence values.
     *
     * @param amount The amount of sequence values to return
     */
    public NextSequenceValueEvent(int amount) {
        this.amount = amount;
    }

    /**
     * Returns the amount of sequence values to return.
     *
     * @return the amount of sequence values to return
     */
    public int getAmount() {
        return amount;
    }

    @Override
    public long[] getResult() {
        return sequenceValues;
    }

    /**
     * Sets the sequence values to return.
     *
     * @param sequenceValues The sequence values
     */
    public void setSequenceValues(long[] sequenceValues) {
        this.sequenceValues = sequenceValues;
    }
}
