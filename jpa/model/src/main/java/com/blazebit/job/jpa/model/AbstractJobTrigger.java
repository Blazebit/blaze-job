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

import com.blazebit.job.Job;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.Schedule;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

/**
 * An abstract mapped superclass implementing the {@link JpaJobTrigger} interface.
 *
 * @param <T> The job type
 * @author Christian Beikov
 * @since 1.0.0
 */
@MappedSuperclass
@Table(name = "job_trigger")
public abstract class AbstractJobTrigger<T extends AbstractJob> extends AbstractJobInstance<Long> implements JpaJobTrigger {

    private static final long serialVersionUID = 1L;

    private T job;
    private String name;
    private JobConfiguration jobConfiguration = new JobConfiguration();

    /**
     * True if overlapping executions of the job are allowed
     */
    private boolean allowOverlap;
    private String scheduleCronExpression;

    /**
     * Creates an empty job trigger.
     */
    protected AbstractJobTrigger() {
    }

    /**
     * Creates a job trigger with the given id.
     *
     * @param id The job trigger id
     */
    protected AbstractJobTrigger(Long id) {
        super(id);
    }

    @Override
    public void onChunkSuccess(JobInstanceProcessingContext<?> processingContext) {
    }

    @NotNull
    @Override
    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }

    /**
     * Sets the given name.
     *
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    // NOTE: We moved the relation from the embedded id to the outer entity because of HHH-10292
    @Override
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "job_trigger_fk_job"))
    public T getJob() {
        return job;
    }

    /**
     * Sets the given job.
     *
     * @param job The job
     */
    public void setJob(T job) {
        this.job = job;
    }

    @Override
    public void setJob(Job job) {
        setJob((T) job);
    }

    @Override
    @Embedded
    public JobConfiguration getJobConfiguration() {
        return jobConfiguration;
    }

    @Transient
    @Override
    public JobConfiguration getOrCreateJobConfiguration() {
        if (getJobConfiguration() == null) {
            setJobConfiguration(new JobConfiguration());
        }
        return getJobConfiguration();
    }

    /**
     * Sets the given job configuration.
     *
     * @param jobConfiguration The job configuration
     */
    public void setJobConfiguration(JobConfiguration jobConfiguration) {
        this.jobConfiguration = jobConfiguration;
    }

    @Override
    @Column(nullable = false)
    public boolean isAllowOverlap() {
        return allowOverlap;
    }

    /**
     * Sets whether overlapping executions are allowed.
     *
     * @param allowOverlap whether overlapping executions are allowed
     */
    public void setAllowOverlap(boolean allowOverlap) {
        this.allowOverlap = allowOverlap;
    }

    /**
     * Returns the cron expression for the schedule.
     *
     * @return the cron expression for the schedule
     */
    @Column(nullable = false)
    public String getScheduleCronExpression() {
        return scheduleCronExpression;
    }

    /**
     * Sets the cron expression for the schedule.
     *
     * @param scheduleCronExpression The cron expression
     */
    public void setScheduleCronExpression(String scheduleCronExpression) {
        this.scheduleCronExpression = scheduleCronExpression;
    }

    @Override
    public Schedule getSchedule(JobContext jobContext) {
        return scheduleCronExpression == null ? null : jobContext.getScheduleFactory().createSchedule(scheduleCronExpression);
    }

}
