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
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceState;

import java.time.Instant;

/**
 * An abstract implementation of the {@link JobInstance} interface.
 *
 * @param <ID> The job instance id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractJobInstance<ID> extends BaseEntity<ID> implements MemoryJobInstance<ID> {

    private static final long serialVersionUID = 1L;

    private long version;
    private JobInstanceState state = JobInstanceState.NEW;

    private int deferCount;
    private Instant scheduleTime;
    private Instant creationTime;
    private Instant lastExecutionTime;

    /**
     * Creates an empty job instance.
     */
    protected AbstractJobInstance() {
    }

    /**
     * Creates a job instance with the given id.
     *
     * @param id The job instance id
     */
    protected AbstractJobInstance(ID id) {
        super(id);
    }

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
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public JobInstanceState getState() {
        return state;
    }

    /**
     * Sets the given state.
     *
     * @param state The state
     */
    public void setState(JobInstanceState state) {
        this.state = state;
    }

    @Override
    public int getDeferCount() {
        return deferCount;
    }

    /**
     * Sets the given defer count.
     *
     * @param deferCount The defer count
     */
    public void setDeferCount(int deferCount) {
        this.deferCount = deferCount;
    }

    @Override
    public Instant getScheduleTime() {
        return scheduleTime;
    }

    @Override
    public void setScheduleTime(Instant scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the given creation time.
     *
     * @param creationTime The creation time
     */
    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    @Override
    public Instant getLastExecutionTime() {
        return lastExecutionTime;
    }

    @Override
    public void setLastExecutionTime(Instant lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }
}
