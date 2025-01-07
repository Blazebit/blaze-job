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

/**
 * An event for replicating changes done to a job instance.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JobInstanceReplicationEvent implements Serializable {

    private final long id;
    private final MemoryJobInstance<?> jobInstance;
    private final boolean removed;

    /**
     * Creates the event with the given job instance.
     *
     * @param id The job instance id
     * @param jobInstance The job instance
     * @param removed Whether the job instance was removed
     */
    public JobInstanceReplicationEvent(long id, MemoryJobInstance<?> jobInstance, boolean removed) {
        this.id = id;
        this.jobInstance = jobInstance;
        this.removed = removed;
    }

    /**
     * Returns the job instance id.
     *
     * @return the job instance id
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the job instance.
     *
     * @return the job instance
     */
    public MemoryJobInstance<?> getJobInstance() {
        return jobInstance;
    }

    /**
     * Returns whether the job instance was removed.
     *
     * @return whether the job instance was removed
     */
    public boolean isRemoved() {
        return removed;
    }
}
