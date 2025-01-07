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

package com.blazebit.job.memory.model;

import com.blazebit.job.JobInstance;

/**
 * An abstract implementation of the {@link JobInstance} interface for trigger based job instances.
 *
 * @param <T> The job trigger type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractTriggerBasedJobInstance<T extends AbstractJobTrigger<? extends AbstractJob>> extends AbstractJobInstance<Long> implements JobInstance<Long> {

    private static final long serialVersionUID = 1L;

    private T trigger;

    /**
     * Creates an empty job instance.
     */
    protected AbstractTriggerBasedJobInstance() {
    }

    /**
     * Creates a job instance with the given id.
     *
     * @param id The job instance id
     */
    protected AbstractTriggerBasedJobInstance(Long id) {
        super(id);
    }

    /**
     * Returns the trigger that created this job instance.
     *
     * @return the trigger that created this job instance
     */
    public T getTrigger() {
        return trigger;
    }

    /**
     * Sets the trigger that created this job instance.
     *
     * @param trigger The trigger
     */
    public void setTrigger(T trigger) {
        this.trigger = trigger;
    }

    @Override
    public Long getPartitionKey() {
        return getId();
    }
}
