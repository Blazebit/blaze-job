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

package com.blazebit.job.jpa.model;

import com.blazebit.job.JobTrigger;

import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * An abstract mapped superclass implementing the {@link JpaTriggerBasedJobInstance} interface.
 *
 * @param <ID> The job instance id type
 * @param <T> The job trigger type
 * @author Christian Beikov
 * @since 1.0.0
 */
@MappedSuperclass
@Table(name = "job_instance")
public abstract class AbstractTriggerBasedJobInstance<ID, T extends AbstractJobTrigger<? extends AbstractJob>> extends AbstractJobInstance<ID> implements JpaTriggerBasedJobInstance<ID> {

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
    protected AbstractTriggerBasedJobInstance(ID id) {
        super(id);
    }

    @NotNull
    @Override
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trigger_id", nullable = false, foreignKey = @ForeignKey(name = "job_instance_fk_job_trigger"))
    public T getTrigger() {
        return trigger;
    }

    /**
     * Sets the given trigger.
     *
     * @param trigger The trigger
     */
    public void setTrigger(T trigger) {
        this.trigger = trigger;
    }

    @Override
    public void setTrigger(JobTrigger jobTrigger) {
        setTrigger((T) jobTrigger);
    }
}
