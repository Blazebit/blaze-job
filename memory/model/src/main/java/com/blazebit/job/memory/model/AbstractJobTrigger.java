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

import com.blazebit.job.JobContext;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.Schedule;

/**
 * An abstract implementation of the {@link JobTrigger} interface.
 *
 * @param <T> The job type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractJobTrigger<T extends AbstractJob> extends AbstractJobInstance<Long> implements JobTrigger {

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

    @Override
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

    @Override
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
    public JobConfiguration getJobConfiguration() {
        return jobConfiguration;
    }

    /**
     * Gets the current or creates a new job configuration and sets it if none available.
     *
     * @return the job configuration
     */
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
