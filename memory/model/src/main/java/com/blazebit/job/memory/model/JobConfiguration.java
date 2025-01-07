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

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple POJO implementation of the {@link com.blazebit.job.JobConfiguration} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JobConfiguration implements com.blazebit.job.JobConfiguration, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether a job may be dropped when deferred more then maximumDeferCount
     */
    private boolean dropable;
    /**
     * The maximum number(inclusive) of times a job may be deferred
     */
    private int maximumDeferCount;
    /**
     * The deadline until which it makes sense to start processing the job
     */
    private Instant deadline;
    /**
     * The time frames within which this job may be executed
     */
    private Set<TimeFrame> executionTimeFrames = new HashSet<>(0);
    /**
     * The parameter for the job
     */
    private Map<String, Serializable> parameters = new HashMap<>(0);

    /**
     * Creates an empty job configuration.
     */
    public JobConfiguration() {
    }

    @Override
    public boolean isDropable() {
        return dropable;
    }

    /**
     * Sets whether the job is dropable due to reaching the maximum defer count.
     *
     * @param dropable whether the job is dropable
     */
    public void setDropable(boolean dropable) {
        this.dropable = dropable;
    }

    @Override
    public int getMaximumDeferCount() {
        return maximumDeferCount;
    }

    /**
     * Sets the maximum defer count.
     *
     * @param maximumDeferCount The maximum defer count
     */
    public void setMaximumDeferCount(int maximumDeferCount) {
        this.maximumDeferCount = maximumDeferCount;
    }

    @Override
    public Instant getDeadline() {
        return deadline;
    }

    /**
     * Sets the deadline.
     *
     * @param deadline The deadline
     */
    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public Set<TimeFrame> getExecutionTimeFrames() {
        return executionTimeFrames;
    }

    /**
     * Sets the execution time frames.
     *
     * @param executionTimeFrames The execution time frames
     */
    public void setExecutionTimeFrames(Set<TimeFrame> executionTimeFrames) {
        this.executionTimeFrames = executionTimeFrames;
    }

    @Override
    public Map<String, Serializable> getParameters() {
        return parameters;
    }

    /**
     * Sets the job parameters.
     *
     * @param parameters The job parameters
     */
    public void setParameters(Map<String, Serializable> parameters) {
        this.parameters = parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobConfiguration)) {
            return false;
        }

        JobConfiguration that = (JobConfiguration) o;

        if (isDropable() != that.isDropable()) {
            return false;
        }
        if (getMaximumDeferCount() != that.getMaximumDeferCount()) {
            return false;
        }
        if (getDeadline() != null ? !getDeadline().equals(that.getDeadline()) : that.getDeadline() != null) {
            return false;
        }
        if (getExecutionTimeFrames() != null ? !getExecutionTimeFrames().equals(that.getExecutionTimeFrames()) : that.getExecutionTimeFrames() != null) {
            return false;
        }
        return getParameters() != null ? getParameters().equals(that.getParameters()) : that.getParameters() == null;

    }

    @Override
    public int hashCode() {
        int result = (isDropable() ? 1 : 0);
        result = 31 * result + getMaximumDeferCount();
        result = 31 * result + (getDeadline() != null ? getDeadline().hashCode() : 0);
        result = 31 * result + (getExecutionTimeFrames() != null ? getExecutionTimeFrames().hashCode() : 0);
        result = 31 * result + (getParameters() != null ? getParameters().hashCode() : 0);
        return result;
    }
}
