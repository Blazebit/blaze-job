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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An embeddable implementing the {@link com.blazebit.job.JobConfiguration} interface.
 * The job parameters and execution time frames are serialized to a LOB to avoid join tables.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@Embeddable
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
    @Column(nullable = false)
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
    @Column(nullable = false)
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

    @Override
    @Transient
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
    @Transient
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

    /**
     * The serializable representing the execution time frames and parameters.
     *
     * @return the serializable
     */
    @Lob
    @Column(name = "parameters")
    protected Serializable getParameterSerializable() {
        Set<TimeFrame> executionTimeFrames = getExecutionTimeFrames();
        Map<String, Serializable> parameters = getParameters();
        if ((executionTimeFrames == null || executionTimeFrames.isEmpty()) && (parameters == null || parameters.isEmpty())) {
            return null;
        }

        return new ParameterSerializable(executionTimeFrames, parameters);
    }

    /**
     * Sets the parameter serializable.
     *
     * @param parameterSerializable The parameter serializable
     */
    protected void setParameterSerializable(Serializable parameterSerializable) {
        if (parameterSerializable instanceof ParameterSerializable) {
            setExecutionTimeFrames(((ParameterSerializable) parameterSerializable).getExecutionTimeFrames());
            setParameters(((ParameterSerializable) parameterSerializable).getParameters());
        } else {
            if (getExecutionTimeFrames() == null) {
                setExecutionTimeFrames(new HashSet<>(0));
            }
            if (getParameters() == null) {
                setParameters(new HashMap<>(0));
            }
        }
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
