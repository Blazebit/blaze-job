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

import com.blazebit.job.memory.model.MemoryJobInstance;

import java.io.Serializable;
import java.util.Collection;

/**
 * The data that is initially replicated.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class InitialReplicationData implements Serializable {

    private final long sequenceValue;
    private final MemoryJobInstance<?>[] jobInstances;

    /**
     * Creates initial replication data.
     *
     * @param sequenceValue The current sequence value
     * @param jobInstances The current job instances
     */
    public InitialReplicationData(long sequenceValue, Collection<MemoryJobInstance<?>> jobInstances) {
        this.sequenceValue = sequenceValue;
        this.jobInstances = jobInstances.toArray(new MemoryJobInstance<?>[0]);
    }

    /**
     * Returns the current sequence value.
     *
     * @return the current sequence value
     */
    public long getSequenceValue() {
        return sequenceValue;
    }

    /**
     * Returns the current job instances.
     *
     * @return the current job instances
     */
    public MemoryJobInstance<?>[] getJobInstances() {
        return jobInstances;
    }
}
