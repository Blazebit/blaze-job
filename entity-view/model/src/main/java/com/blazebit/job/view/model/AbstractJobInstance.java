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

package com.blazebit.job.view.model;

import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceState;
import com.blazebit.persistence.view.PrePersist;

import java.time.Instant;

/**
 * An abstract entity view implementing the {@link JobInstance} interface.
 *
 * @param <ID> The job instance id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractJobInstance<ID> implements JobInstance<ID>, IdHolderView<ID> {

    private static final long serialVersionUID = 1L;

    @Override
    public void incrementDeferCount() {
        setDeferCount(getDeferCount() + 1);
    }

    @Override
    public void markDeadlineReached(JobInstanceProcessingContext<?> jobProcessingContext) {
        setState(JobInstanceState.DEADLINE_REACHED);
    }

    @Override
    public void markDropped(JobInstanceProcessingContext<?> jobProcessingContext) {
        setState(JobInstanceState.DROPPED);
    }

    @Override
    public void markRunning(JobInstanceProcessingContext<?> processingContext) {
        setState(JobInstanceState.RUNNING);
    }

    @Override
    public void markDone(JobInstanceProcessingContext<?> jobProcessingContext, Object result) {
        setState(JobInstanceState.DONE);
    }

    @Override
    public void markFailed(JobInstanceProcessingContext<?> jobProcessingContext, Throwable t) {
        setState(JobInstanceState.FAILED);
    }

    @Override
    public abstract JobConfigurationView getJobConfiguration();

    /**
     * Sets the given state.
     *
     * @param state The state
     */
    protected abstract void setState(JobInstanceState state);

    /**
     * Sets the given defer count.
     *
     * @param deferCount The defer count
     */
    public abstract void setDeferCount(int deferCount);

    /**
     * Sets the given creation time.
     *
     * @param creationTime The creation time
     */
    public abstract void setCreationTime(Instant creationTime);

    /**
     * A {@link PrePersist} method that sets the creation time if necessary.
     */
    @PrePersist
    protected void onPersist() {
        if (getCreationTime() == null) {
            setCreationTime(Instant.now());
        }
    }
}
